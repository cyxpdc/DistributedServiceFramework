package ares.remoting.framework.provider;

import ares.remoting.framework.model.AresRequest;
import ares.remoting.framework.model.AresResponse;
import ares.remoting.framework.model.ProviderService;
import ares.remoting.framework.zookeeper.RegisterCenter;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Collections2;
import com.google.common.collect.Maps;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 处理服务端的逻辑
 * 根据客户端传过来的服务调用对象，返回调用方法后的结果
 * @author pdc
 */
@ChannelHandler.Sharable
public class NettyServerInvokeHandler extends SimpleChannelInboundHandler<AresRequest> {

    private static final ThreadLocal<String> TRANSFER_ID = new ThreadLocal<>();

    private static final Logger LOGGER = LoggerFactory.getLogger(NettyServerInvokeHandler.class);

    /**
     * 服务端限流，一个接口对应一个限流工具
     */
    private static final Map<String, Semaphore> SERVICE_KEY_SEMAPHORE_MAP = Maps.newConcurrentMap();

    //private static final SimpleLimiter LIMITER = new SimpleLimiter();

    /**
     * 接收客户端发来的数据，调用方法，然后将方法返回的结果写回客户端
     * @param ctx
     * @param request
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, AresRequest request){
        if (ctx.channel().isWritable()) {
            TRANSFER_ID.set(request.getUniqueKey());
            //从服务调用对象里获取通过软负载算法后得到的服务提供者信息
            ProviderService metaDataModel = request.getProviderService();
            final long consumeTimeOut = request.getInvokeTimeout();
            final String methodName = request.getInvokedMethodName();
            //根据方法名称定位到具体某一个服务提供者接口名
            String serviceKey = metaDataModel.getServiceItf().getName();
            //获取限流工具类，需要保证线程安全
            Semaphore semaphore = getSemaphore(serviceKey, metaDataModel.getWorkerThreads());
            //从注册中心获取该接口对应的服务提供者列表
            List<ProviderService> localProviderCaches = RegisterCenter.singleton()
                                            .getProviderServiceMap().get(serviceKey);

            Object result = null;
            boolean acquire = false;

            try {
                //获取拥有指定方法名的服务提供者之一
                ProviderService localProviderCache = Collections2.filter(localProviderCaches,
                                input ->
                                StringUtils.equals(input.getServiceMethod().getName(), methodName))
                                .iterator().next();
                //利用反射发起服务调用，利用semaphore实现限流
                Object serviceObject = localProviderCache.getServiceObject();
                Method method = localProviderCache.getServiceMethod();
                //LIMITER.acquire();
                acquire = semaphore.tryAcquire(consumeTimeOut, TimeUnit.MILLISECONDS);
                if (acquire) {
                    result = method.invoke(serviceObject, request.getArgs());
                }
            } catch (IllegalAccessException |  InterruptedException | InvocationTargetException e) {
                System.out.println(JSON.toJSONString(localProviderCaches) + "  " + methodName+" " + e.getMessage());
                result = e;//恢复
            } finally {
                if (acquire) {
                    semaphore.release();
                }
            }
            //根据服务调用结果组装调用返回对象
            AresResponse response = new AresResponse();
            response.setInvokeTimeout(consumeTimeOut);
            response.setUniqueKey(request.getUniqueKey());
            response.setResult(result);
            //将服务调用返回对象回写到消费端
            ctx.writeAndFlush(response);
        } else {
            LOGGER.error("------------channel closed!---------------");
        }
    }

    private Semaphore getSemaphore(String serviceKey, int workerThread) {
        Semaphore semaphore = SERVICE_KEY_SEMAPHORE_MAP.get(serviceKey);
        if (semaphore == null) {
            synchronized (SERVICE_KEY_SEMAPHORE_MAP) {
                semaphore = SERVICE_KEY_SEMAPHORE_MAP.get(serviceKey);
                if (semaphore == null) {
                    semaphore = new Semaphore(workerThread);
                    SERVICE_KEY_SEMAPHORE_MAP.put(serviceKey, semaphore);
                }
            }
        }
        return semaphore;
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx){
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        //发生异常,关闭链路
        ctx.close();
    }
}
