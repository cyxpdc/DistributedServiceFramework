package ares.remoting.framework.revoker;

import ares.remoting.framework.cluster.ClusterStrategy;
import ares.remoting.framework.cluster.engine.ClusterEngine;
import ares.remoting.framework.model.AresRequest;
import ares.remoting.framework.model.AresResponse;
import ares.remoting.framework.model.ProviderService;
import ares.remoting.framework.zookeeper.RegisterCenter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 消费端bean代理工厂
 * 使用动态代理创建服务提供者的消费端代理对象
 *
 * @author pdc
 */
public class RevokerProxyBeanFactory implements InvocationHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RevokerProxyBeanFactory.class);
    /**
     * 这里不需要volatile，因为指令重排序不会影响线程安全
     */
    private ExecutorService fixedThreadPool = null;

    private static volatile RevokerProxyBeanFactory singleton;

    //服务接口
    private Class<?> targetInterface;
    //超时时间
    private int consumeTimeout;
    //调用者线程数
    private static int threadWorkerNumber = 10;
    //负载均衡策略
    private String clusterStrategy;


    public RevokerProxyBeanFactory(Class<?> targetInterface, int consumeTimeout, String clusterStrategy) {
        this.targetInterface = targetInterface;
        this.consumeTimeout = consumeTimeout;
        this.clusterStrategy = clusterStrategy;
    }

    public static RevokerProxyBeanFactory singleton(Class<?> targetInterface, int consumeTimeout, String clusterStrategy) throws Exception {
        if (null == singleton) {
            synchronized (RevokerProxyBeanFactory.class) {
                if (null == singleton) {
                    singleton = new RevokerProxyBeanFactory(targetInterface, consumeTimeout, clusterStrategy);
                }
            }
        }
        return singleton;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        //服务接口名称
        String serviceKey = targetInterface.getName();
        List<ProviderService> providerServices = RegisterCenter.singleton().getServiceMetaDataMap4Consume().get(serviceKey);
        //根据软负载策略,从服务提供者列表选取本次调用的服务提供者
        ClusterStrategy clusterStrategyService = ClusterEngine.queryClusterStrategy(clusterStrategy);
        ProviderService providerService = clusterStrategyService.select(providerServices);
        //复制一份服务提供者信息
        ProviderService curProvider = providerService.copy();
        //设置本次调用服务的方法以及接口
        curProvider.setServiceMethod(method);
        curProvider.setServiceItf(targetInterface);

        //声明调用AresRequest对象,AresRequest表示发起一次调用所包含的信息
        final AresRequest request = new AresRequest();
        request.setUniqueKey(UUID.randomUUID().toString() + "-" + Thread.currentThread().getId());
        request.setProviderService(curProvider);
        request.setInvokeTimeout(consumeTimeout);
        request.setInvokedMethodName(method.getName());
        request.setArgs(args);

        try {
            //构建用来发起调用的线程池,使用固定数目的n个线程，使用无界队列LinkedBlockingQueue，线程创建后不会超时终止。
            //如果任务过多，某个任务执行过慢，可能会出现出现OOM，可以自定义队列为ArrayBlockingQueue或Disruptor
            if (fixedThreadPool == null) {
                synchronized (RevokerProxyBeanFactory.class) {
                    if (null == fixedThreadPool) {
                        fixedThreadPool = Executors.newFixedThreadPool(threadWorkerNumber);
                    }
                }
            }
            //根据服务提供者的ip,port,构建InetSocketAddress对象,标识服务提供者地址
            String serverIp = curProvider.getServerIp();
            int serverPort = curProvider.getServerPort();
            InetSocketAddress inetSocketAddress = new InetSocketAddress(serverIp, serverPort);
            //提交本次调用信息到线程池fixedThreadPool,发起调用；使用Future保证任务只能被执行一次
            Future<AresResponse> responseFuture = fixedThreadPool.submit(RevokerServiceCallable.of(inetSocketAddress, request));
            //获取调用的返回结果
            AresResponse response = responseFuture.get(request.getInvokeTimeout(), TimeUnit.MILLISECONDS);
            if (response != null) {
                return response.getResult();
            }
        } catch (Exception e) {
            LOGGER.error("RevokerProxyBeanFactory invoke failure ： " + e);
            throw new RuntimeException(e);
        }
        return null;
    }

    public Object getProxy() {
        return Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{targetInterface},
                this);
    }
}
