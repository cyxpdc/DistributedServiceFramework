package ares.remoting.framework.cluster;

import ares.remoting.framework.model.ProviderService;

import java.util.List;

/**
 * @author pdc
 */
public interface ClusterStrategy {

    /**
     * 负载策略算法
     *
     * @param providerServices
     * @return
     */
    public ProviderService select(List<ProviderService> providerServices);
}
