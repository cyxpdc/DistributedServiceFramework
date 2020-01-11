package ares.remoting.framework.cluster.impl;

import ares.remoting.framework.cluster.ClusterStrategy;
import ares.remoting.framework.model.ProviderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 软负载轮询算法实现
 * @author pdc
 */
public class PollingClusterStrategyImpl implements ClusterStrategy {

    private static final Logger LOGGER = LoggerFactory.getLogger(PollingClusterStrategyImpl.class);

    /**
     * 计数器和锁，锁用来保护计数器
     */
    private int index = 0;
    private Lock lock = new ReentrantLock();

    @Override
    public ProviderService select(List<ProviderService> providerServices) {

        ProviderService service = null;
        try {
            lock.tryLock(10, TimeUnit.MILLISECONDS);
            //若计数大于服务提供者个数,将计数器归0
            //如果这里不加判断，而是使用providerServices.get(index % providerServices.size())的方式
            //则有可能index溢出，要多加一个判断，不值得
            if (index >= providerServices.size()) {
                index = 0;
            }
            service = providerServices.get(index);
            index++;

        } catch (InterruptedException e) {
            LOGGER.error("PollingClusterStrategyImpl select failure" + e);
        } finally {
            lock.unlock();
        }
        //兜底,保证程序健壮性,若未取到服务,则直接取第一个
        if (service == null) {
            service = providerServices.get(0);
        }
        return service;
    }
}
