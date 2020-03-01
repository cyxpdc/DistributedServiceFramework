package ares.remoting.framework.revoker;

import ares.remoting.framework.model.AresResponse;
import ares.remoting.framework.model.AresResponseWrapper;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 保存及操作返回结果的数据容器类
 * @author pdc
 */
public class RevokerResponseHolder {

    private static final Logger LOGGER = LoggerFactory.getLogger(RevokerResponseHolder.class);
    /**
     * 服务返回结果Map，key为唯一标识本次调用
     */
    private static final Map<String, AresResponseWrapper> RESPONSE_WRAPPER_MAP = Maps.newConcurrentMap();
    /**
     * 清除过期的返回结果，建议手动创建线程池
     */
    private static final ExecutorService REMOVE_EXPIRE_KEY_EXECUTOR = Executors.newSingleThreadExecutor();

    /**
     * 删除超时未获取到结果的key,防止内存泄露
     */
    static {
        REMOVE_EXPIRE_KEY_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        for (Map.Entry<String, AresResponseWrapper> entry : RESPONSE_WRAPPER_MAP.entrySet()) {
                            boolean isExpire = entry.getValue().isExpire();
                            if (isExpire) {
                                RESPONSE_WRAPPER_MAP.remove(entry.getKey());
                            }
                            Thread.sleep(10);
                        }
                    } catch (InterruptedException e) {
                        LOGGER.warn("Thread.sleep failure" + e);
                    }

                }
            }
        });
    }

    /**
     * 初始化返回结果容器,requestUniqueKey唯一标识本次调用
     * @param requestUniqueKey
     */
    public static void initResponseData(String requestUniqueKey) {
        RESPONSE_WRAPPER_MAP.put(requestUniqueKey, AresResponseWrapper.of());
    }


    /**
     * 由NettyClientInvokeHandler#channelRead0调用，将Netty调用异步返回结果放入阻塞队列
     * 最终由RevokerServiceCallable#call调用
     * @param response
     */
    public static void putResultValue(AresResponse response) {
        long currentTime = System.currentTimeMillis();
        AresResponseWrapper responseWrapper = RESPONSE_WRAPPER_MAP.get(response.getUniqueKey());
        responseWrapper.setResponseTime(currentTime);
        responseWrapper.getResponseQueue().add(response);
        RESPONSE_WRAPPER_MAP.put(response.getUniqueKey(), responseWrapper);
    }


    /**
     * 从阻塞队列中获取Netty异步返回的结果值
     * @param requestUniqueKey
     * @param timeout
     * @return
     */
    public static AresResponse getValue(String requestUniqueKey, long timeout) {
        AresResponseWrapper responseWrapper = RESPONSE_WRAPPER_MAP.get(requestUniqueKey);
        try {
            return responseWrapper.getResponseQueue().poll(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            RESPONSE_WRAPPER_MAP.remove(requestUniqueKey);
        }
    }
}
