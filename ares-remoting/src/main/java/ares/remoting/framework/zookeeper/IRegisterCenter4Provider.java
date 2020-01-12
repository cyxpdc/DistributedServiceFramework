package ares.remoting.framework.zookeeper;

import ares.remoting.framework.model.ProviderService;

import java.util.List;
import java.util.Map;

/**
 * 服务端注册中心接口
 * @author pdc
 */
public interface IRegisterCenter4Provider {


    /**
     * 服务端将服务提供者信息注册到zk对应的节点下
     * @param serviceMetaData 服务提供者信息列表
     */
    public void registerProvider(final List<ProviderService> serviceMetaData);


    /**
     * 服务端获取服务提供者信息
     * @return 返回对象的Key为服务提供者接口名，value:服务提供者服务方法列表
     */
    public Map<String, List<ProviderService>> getProviderServiceMap();


}
