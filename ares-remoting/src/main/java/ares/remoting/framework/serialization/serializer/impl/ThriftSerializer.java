package ares.remoting.framework.serialization.serializer.impl;

import ares.remoting.framework.serialization.serializer.ISerializer;
import org.apache.thrift.TBase;
import org.apache.thrift.TDeserializer;
import org.apache.thrift.TException;
import org.apache.thrift.TSerializer;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TCompactProtocol;

/**
 * @author pdc
 */
public class ThriftSerializer implements ISerializer {

    @Override
    public <T> byte[] serialize(T obj) {
        try {
            if (!(obj instanceof TBase)) {
                throw new UnsupportedOperationException("not supported obj type");
            }
            TSerializer serializer = new TSerializer(new TCompactProtocol.Factory());
            return serializer.serialize((TBase) obj);
        } catch (TException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <T> T deserialize(byte[] data, Class<T> cls) {
        try {
            if (!TBase.class.isAssignableFrom(cls)) {
                throw new UnsupportedOperationException("not supported obj type");
            }
            //可以指定如下序列化协议
            //TBinaryProtocol  TJSONProtocol  TCompactProtocol
            TBase o = (TBase) cls.newInstance();
            TDeserializer tDeserializer = new TDeserializer(new TCompactProtocol.Factory());
            tDeserializer.deserialize(o, data);
            return (T) o;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
