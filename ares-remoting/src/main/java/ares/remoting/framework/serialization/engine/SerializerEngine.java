package ares.remoting.framework.serialization.engine;


import ares.remoting.framework.serialization.common.SerializeType;
import ares.remoting.framework.serialization.serializer.ISerializer;
import ares.remoting.framework.serialization.serializer.impl.*;
import avro.shaded.com.google.common.collect.Maps;

import java.util.Map;

/**
 * @author pdc
 */
public class SerializerEngine {

    public static final Map<SerializeType, ISerializer> SERIALIZER_MAP = Maps.newConcurrentMap();

    static {
        SERIALIZER_MAP.put(SerializeType.DefaultJavaSerializer, new DefaultJavaSerializer());
        SERIALIZER_MAP.put(SerializeType.HessianSerializer, new HessianSerializer());
        SERIALIZER_MAP.put(SerializeType.JSONSerializer, new JSONSerializer());
        SERIALIZER_MAP.put(SerializeType.XmlSerializer, new XmlSerializer());
        SERIALIZER_MAP.put(SerializeType.ProtoStuffSerializer, new ProtoStuffSerializer());
        SERIALIZER_MAP.put(SerializeType.MarshallingSerializer, new MarshallingSerializer());

        //以下三类不能使用普通的java bean
        SERIALIZER_MAP.put(SerializeType.AvroSerializer, new AvroSerializer());
        SERIALIZER_MAP.put(SerializeType.ThriftSerializer, new ThriftSerializer());
        SERIALIZER_MAP.put(SerializeType.ProtocolBufferSerializer, new ProtocolBufferSerializer());
    }


    public static <T> byte[] serialize(T obj, String serializeType) {
        SerializeType serialize = SerializeType.queryByType(serializeType);
        if (serialize == null) {
            throw new RuntimeException("serialize is  null");
        }
        ISerializer serializer = SERIALIZER_MAP.get(serialize);
        if (serializer == null) {
            throw new RuntimeException("serialize error");
        }
        return serializer.serialize(obj);
    }


    public static <T> T deserialize(byte[] data, Class<T> clazz, String serializeType) {
        SerializeType serialize = SerializeType.queryByType(serializeType);
        if (serialize == null) {
            throw new RuntimeException("serialize is null");
        }
        ISerializer serializer = SERIALIZER_MAP.get(serialize);
        if (serializer == null) {
            throw new RuntimeException("serialize error");
        }
        return serializer.deserialize(data, clazz);
    }
}
