package ares.remoting.framework.helper;

import ares.remoting.framework.serialization.common.SerializeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * 配置项的获取助手
 * @author pdc
 */
public class PropertyConfigeHelper {

    private static final Logger logger = LoggerFactory.getLogger(PropertyConfigeHelper.class);

    private static final String PROPERTY_CLASSPATH = "/ares_remoting.properties";
    private static final Properties PROPERTIES = new Properties();

    //ZK服务地址
    private static final String zkService;
    //ZK session超时时间
    private static final int ZK_SESSION_TIMEOUT;
    //ZK connection超时时间
    private static final int ZK_CONNECTION_TIMEOUT;
    //序列化算法类型
    private static final SerializeType SERIALIZE_TYPE;
    //每个服务端提供者的Netty的连接数
    private static final int CHANNEL_CONNECT_SIZE;


    /**
     * 初始化
     */
    static {
        InputStream is = null;
        try {
            is = PropertyConfigeHelper.class.getResourceAsStream(PROPERTY_CLASSPATH);
            if (null == is) {
                throw new IllegalStateException("ares_remoting.properties can not found in the classpath.");
            }
            PROPERTIES.load(is);

            zkService = PROPERTIES.getProperty("zk_service");
            ZK_SESSION_TIMEOUT = Integer.parseInt(PROPERTIES.getProperty("zk_sessionTimeout", "500"));
            ZK_CONNECTION_TIMEOUT = Integer.parseInt(PROPERTIES.getProperty("zk_connectionTimeout", "500"));
            CHANNEL_CONNECT_SIZE = Integer.parseInt(PROPERTIES.getProperty("channel_connect_size", "10"));
            SERIALIZE_TYPE = SerializeType.queryByType(PROPERTIES.getProperty("serialize_type","ProtoStuffSerializer"));
        } catch (IOException e) {
            logger.warn("load ares_remoting's properties file failed.", e);
            throw new RuntimeException(e);
        } finally {
            if (null != is) {
                try {
                    is.close();
                } catch (IOException e) {
                    logger.error("close InputStream failure" + e);
                }
            }
        }
    }

    public static String getZkService() {
        return zkService;
    }

    public static int getZkSessionTimeout() {
        return ZK_SESSION_TIMEOUT;
    }

    public static int getZkConnectionTimeout() {
        return ZK_CONNECTION_TIMEOUT;
    }

    public static int getChannelConnectSize() {
        return CHANNEL_CONNECT_SIZE;
    }

    public static SerializeType getSerializeType() {
        return SERIALIZE_TYPE;
    }
}
