package ares.remoting.framework.serialization.serializer.impl;

import ares.remoting.framework.serialization.serializer.ISerializer;
import com.google.protobuf.GeneratedMessageV3;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;

/**
 * 是Google的一种数据交换格式，独立于语言、平台；是一个纯粹的应用层协议，可以和各种运输层协议一起使用
 * 空间开销小，解析性能高
 * @author pdc
 */
public class ProtocolBufferSerializer implements ISerializer {

    private final Logger LOGGER = LoggerFactory.getLogger(ProtocolBufferSerializer.class);

    /**
     * 构造完对象后就调用此方法
     * @param obj 对象
     * @param <T> 返回的字节数据
     * @return
     */
    @Override
    public <T> byte[] serialize(T obj){
        try {
            //需要其父类为GeneratedMessageV3
            if (!(obj instanceof GeneratedMessageV3)) {
                throw new UnsupportedOperationException("not supported obj type");
            }
            return (byte[]) MethodUtils.invokeMethod(obj, "toByteArray");
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            LOGGER.error("序列化出错"+e);
        }
        return null;
    }

    @Override
    public <T> T deserialize(byte[] data, Class<T> cls) {
        try {
            //需要其父类为GeneratedMessageV3
            if (!GeneratedMessageV3.class.isAssignableFrom(cls)) {
                throw new UnsupportedOperationException("not supported obj type");
            }
            Object o = MethodUtils.invokeStaticMethod(cls, "getDefaultInstance");
            return (T) MethodUtils.invokeMethod(o, "parseFrom", new Object[]{data});
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            LOGGER.error("反序列化出错"+e);
        }
        return null;
    }
}
