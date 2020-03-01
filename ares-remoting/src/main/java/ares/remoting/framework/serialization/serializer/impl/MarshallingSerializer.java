package ares.remoting.framework.serialization.serializer.impl;

import ares.remoting.framework.serialization.serializer.ISerializer;
import org.jboss.marshalling.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Java对象序列包，兼容Java原生的序列化机制，对Java原生序列化机制进行了优化，提升了性能
 * 在保持跟Serializble接口兼容的同时增加了一些可调的参数和附加特性，可通过工厂类进行配置
 * 是一个很好的替代原生Java序列化的框架
 * @author pdc
 */
public class MarshallingSerializer implements ISerializer {

    private final Logger LOGGER = LoggerFactory.getLogger(MarshallingSerializer.class);

    final static MarshallingConfiguration CONFIGURATION = new MarshallingConfiguration();
    //获取序列化工厂对象,参数serial标识创建的是java序列化工厂对象
    final static MarshallerFactory MARSHALLER_FACTORY = Marshalling.getProvidedMarshallerFactory("serial");

    static {
        CONFIGURATION.setVersion(5);
    }

    @Override
    public byte[] serialize(Object obj) {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            final Marshaller marshaller = MARSHALLER_FACTORY.createMarshaller(CONFIGURATION);
            marshaller.start(Marshalling.createByteOutput(byteArrayOutputStream));
            marshaller.writeObject(obj);
            marshaller.finish();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return byteArrayOutputStream.toByteArray();
    }

    @Override
    public <T> T deserialize(byte[] data, Class<T> clazz) {
        try {
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(data);
            final Unmarshaller unmarshaller = MARSHALLER_FACTORY.createUnmarshaller(CONFIGURATION);
            unmarshaller.start(Marshalling.createByteInput(byteArrayInputStream));
            Object object = unmarshaller.readObject();
            unmarshaller.finish();
            return (T) object;
        } catch (Exception e) {
            LOGGER.error("MarshallingSerializer反序列化失败：" + e);
        }
        return null;
    }
}