package ares.remoting.framework.zookeeper;

import ares.remoting.framework.model.InvokerService;
import ares.remoting.framework.model.ProviderService;

import java.util.List;
import java.util.Map;

/**
 * 消费端注册中心接口
 * @author pdc
 */
public interface IRegisterCenter4Invoker {

    /**
     * 消费端初始化服务提供者信息到本地缓存
     *
     * @param remoteAppKey
     * @param groupName
     */
    public void initProviderMap(String remoteAppKey, String groupName);

    /**
     * 消费端获取服务提供者信息
     *
     * @return
     */
    public Map<String, List<ProviderService>> getServiceMetaDataMap4Consume();

    /**
     * 消费端将消费者信息注册到zk对应的节点下
     *
     * @param invoker
     */
    public void registerInvoker(final InvokerService invoker);
}
