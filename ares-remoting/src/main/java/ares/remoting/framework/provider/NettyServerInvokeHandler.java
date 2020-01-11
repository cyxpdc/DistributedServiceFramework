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

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 处理服务端的逻辑
 * 根据客户端传过来的服务调用对象，返回调用方法后的结果
 *
 * @author pdc
 */
@ChannelHandler.Sharable
public class NettyServerInvokeHandler extends SimpleChannelInboundHandler<AresRequest> {

    private static final Logger logger = LoggerFactory.getLogger(NettyServerInvokeHandler.class);

    /**
     * 服务端限流
     * K为服务接口，V为Semaphore
     */
    private static final Map<String, Semaphore> serviceKeySemaphoreMap = Maps.newConcurrentMap();

    /**
     * 接收客户端发来的数据，写回客户端
     * @param ctx
     * @param request
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, AresRequest request){
        if (ctx.channel().isWritable()) {
            //从服务调用对象里获取通过软负载算法后得到的服务提供者信息
            ProviderService metaDataModel = request.getProviderService();
            final long consumeTimeOut = request.getInvokeTimeout();
            final String methodName = request.getInvokedMethodName();
            //根据方法名称定位到具体某一个服务提供者接口名
            String serviceKey = metaDataModel.getServiceInterface().getName();
            //获取限流工具类，保证线程安全
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
                //利用反射发起服务调用
                Object serviceObject = localProviderCache.getServiceObject();
                Method method = localProviderCache.getServiceMethod();
                //利用semaphore实现限流
                acquire = semaphore.tryAcquire(consumeTimeOut, TimeUnit.MILLISECONDS);
                if (acquire) {
                    result = method.invoke(serviceObject, request.getArgs());
                }
            } catch (Exception e) {
                System.out.println(JSON.toJSONString(localProviderCaches) + "  " + methodName+" "+e.getMessage());
                result = e;
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
            logger.error("------------channel closed!---------------");
        }
    }

    private Semaphore getSemaphore(String serviceKey, int workerThread) {
        Semaphore semaphore = serviceKeySemaphoreMap.get(serviceKey);
        if (semaphore == null) {
            synchronized (serviceKeySemaphoreMap) {
                semaphore = serviceKeySemaphoreMap.get(serviceKey);
                if (semaphore == null) {
                    semaphore = new Semaphore(workerThread);
                    serviceKeySemaphoreMap.put(serviceKey, semaphore);
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
