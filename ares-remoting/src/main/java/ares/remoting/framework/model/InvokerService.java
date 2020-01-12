package ares.remoting.framework.model;

import java.io.Serializable;
import java.lang.reflect.Method;

/**
 * 消费者信息
 * @author pdc
 */
public class InvokerService implements Serializable {
    /**
     * 服务接口
     */
    private Class<?> serviceItf;
    /**
     * 服务bean
     */
    private Object serviceObject;

    private Method serviceMethod;

    private String invokerIp;

    private int invokerPort;
    /**
     * 超时时间
     */
    private long timeout;
    /**
     * 服务提供者唯一标识
     */
    private String remoteAppKey;
    /**
     * 服务分组组名
     */
    private String groupName = "default";

    public Class<?> getServiceItf() {
        return serviceItf;
    }

    public void setServiceItf(Class<?> serviceItf) {
        this.serviceItf = serviceItf;
    }

    public void setInvokerIp(String invokerIp) {
        this.invokerIp = invokerIp;
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
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
