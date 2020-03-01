package ares.remoting.framework.cluster.engine;

import ares.remoting.framework.cluster.ClusterStrategy;
import ares.remoting.framework.cluster.impl.*;
import avro.shaded.com.google.common.collect.Maps;

import java.util.Map;

/**
 * 负载均衡引擎
 *
 * @author pdc
 */
public class ClusterEngine {

    private static final Map<ClusterStrategyEnum, ClusterStrategy> CLUSTER_STRATEGY_MAP = Maps.newConcurrentMap();

    static {
        CLUSTER_STRATEGY_MAP.put(ClusterStrategyEnum.Random, new RandomClusterStrategyImpl());
        CLUSTER_STRATEGY_MAP.put(ClusterStrategyEnum.WeightRandom, new WeightRandomClusterStrategyImpl());
        CLUSTER_STRATEGY_MAP.put(ClusterStrategyEnum.Polling, new PollingClusterStrategyImpl());
        CLUSTER_STRATEGY_MAP.put(ClusterStrategyEnum.WeightPolling, new WeightPollingClusterStrategyImpl());
        CLUSTER_STRATEGY_MAP.put(ClusterStrategyEnum.Hash, new HashClusterStrategyImpl());
    }

    /**
     * 对外统一API
     * @param clusterStrategy
     * @return
     */
    public static ClusterStrategy queryClusterStrategy(String clusterStrategy) {
        ClusterStrategyEnum clusterStrategyEnum = ClusterStrategyEnum.queryByCode(clusterStrategy);
        if (clusterStrategyEnum == null) {
            //默认选择随机算法
            return new RandomClusterStrategyImpl();
        }
        return CLUSTER_STRATEGY_MAP.get(clusterStrategyEnum);
    }
}
