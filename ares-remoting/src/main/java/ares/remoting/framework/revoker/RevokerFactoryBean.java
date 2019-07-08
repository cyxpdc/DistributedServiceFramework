package ares.remoting.framework.revoker;

import ares.remoting.framework.model.InvokerService;
import ares.remoting.framework.model.ProviderService;
import ares.remoting.framework.zookeeper.IRegisterCenter4Invoker;
import ares.remoting.framework.zookeeper.RegisterCenter;
import org.apache.commons.collections.MapUtils;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;

import java.util.List;
import java.util.Map;

/**
 * 服务bean引入入口
 *
 * @author pdc
 */
public class RevokerFactoryBean implements FactoryBean, InitializingBean {

    /**
     * 服务接口
     */
    private Class<?> targetInterface;
    /**
     * 超时时间
     */
    private int timeout;
    /**
     * 服务bean：
     * 远程服务生成的调用方本地代理对象，可以看作调用方stub
     */
    private Object serviceObject;
    /**
     * 负载均衡策略
     */
    private String clusterStrategy;
    /**
     * 服务提供者唯一标识
     */
    private String remoteAppKey;
    /**
     * 服务分组组名
     */
    private String groupName = "default";

    /**
     * context.getbean真正获得的对象
     * 返回服务提供者代理对象
     * @return
     * @throws Exception
     */
    @Override
    public Object getObject() {
        return serviceObject;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        //获取服务注册中心
        IRegisterCenter4Invoker registerCenter4Consumer = RegisterCenter.singleton();
        //初始化服务提供者列表到消费端本地缓存serviceMetaDataMap4Consume
        registerCenter4Consumer.initProviderMap(remoteAppKey, groupName);
        //初始化Netty Channel
        Map<String, List<ProviderService>> providerMap = registerCenter4Consumer.getServiceMetaDataMap4Consume();
        if (MapUtils.isEmpty(providerMap)) {
            throw new RuntimeException("service provider list is empty.");
        }
        NettyChannelPoolFactory.singleton().initChannelPoolFactory(providerMap);
        //获取服务提供者代理对象
        RevokerProxyBeanFactory proxyFactory = RevokerProxyBeanFactory.singleton(targetInterface, timeout, clusterStrategy);
        this.serviceObject = proxyFactory.getProxy();
        //将消费者信息注册到注册中心
        InvokerService invoker = new InvokerService();
        invoker.setServiceItf(targetInterface);
        invoker.setRemoteAppKey(remoteAppKey);
        invoker.setGroupName(groupName);
        registerCenter4Consumer.registerInvoker(invoker);
    }

    @Override
    public Class<?> getObjectType() {
        return targetInterface;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    public Class<?> getTargetInterface() {
        return targetInterface;
    }

    public void setTargetInterface(Class<?> targetInterface) {
        this.targetInterface = targetInterface;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public Object getServiceObject() {
        return serviceObject;
    }

    public void setServiceObject(Object serviceObject) {
        this.serviceObject = serviceObject;
    }

    public String getClusterStrategy() {
        return clusterStrategy;
    }

    public void setClusterStrategy(String clusterStrategy) {
        this.clusterStrategy = clusterStrategy;
    }

    public String getRemoteAppKey() {
        return remoteAppKey;
    }

    public void setRemoteAppKey(String remoteAppKey) {
        this.remoteAppKey = remoteAppKey;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }
}
