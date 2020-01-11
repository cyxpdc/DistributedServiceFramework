package ares.remoting.framework.cluster.impl;

import ares.remoting.framework.cluster.ClusterStrategy;
import ares.remoting.framework.model.ProviderService;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.RandomUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 软负载加权随机算法实现
 * @author pdc
 */
public class WeightRandomClusterStrategyImpl implements ClusterStrategy {

    /*@Override
    public ProviderService select(List<ProviderService> providerServices) {
        //存放加权后的服务提供者列表
        List<ProviderService> providerList = Lists.newArrayList();
        for (ProviderService provider : providerServices) {
            int weight = provider.getWeight();
            for (int i = 0; i < weight; i++) {
                providerList.add(provider.copy());
            }
        }
        int MAX_LEN = providerList.size();
        int index = RandomUtils.nextInt(0, MAX_LEN - 1);
        return providerList.get(index);
    }*/

    @Override
    public ProviderService select(List<ProviderService> providerServices) {
        //上面的实现很耗费内存，这里使用map会好点，kv为顺序：服务，且可以不使用copy方法
        Map<Integer,ProviderService> map = new HashMap<>();
        int cur = 0;
        for(ProviderService provider : providerServices){
            int weight = provider.getWeight();
            for(int i = 0;i < weight;i++){
                map.put(cur++,provider);
            }
        }
        int MAX_LEN = map.size();
        int index = RandomUtils.nextInt(0, MAX_LEN - 1);
        return map.get(index);
    }
}
