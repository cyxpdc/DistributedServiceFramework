package ares.remoting.framework.cluster.engine;

import org.apache.commons.lang.StringUtils;

/**
 * @author pdc
 */
public enum ClusterStrategyEnum {
    //随机算法
    Random("Random"),
    //权重随机算法
    WeightRandom("WeightRandom"),
    //轮询算法
    Polling("Polling"),
    //权重轮询算法
    WeightPolling("WeightPolling"),
    //源地址hash算法
    Hash("Hash");

    private String clusterStrategy;

    private ClusterStrategyEnum(String clusterStrategy) {
        this.clusterStrategy = clusterStrategy;
    }


    public static ClusterStrategyEnum queryByCode(String clusterStrategy) {
        if (StringUtils.isBlank(clusterStrategy)) {
            return null;
        }
        for (ClusterStrategyEnum strategy : values()) {
            if (StringUtils.equals(clusterStrategy, strategy.getClusterStrategy())) {
                return strategy;
            }
        }
        return null;
    }

    public String getClusterStrategy() {
        return clusterStrategy;
    }
}
