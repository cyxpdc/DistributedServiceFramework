package ares.remoting.framework.serialization.serializer.impl;

import ares.remoting.framework.serialization.serializer.ISerializer;
import com.caucho.hessian.io.HessianInput;
import com.caucho.hessian.io.HessianOutput;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * 支持跨语言的二进制序列化协议，相对于Java默认序列化，具有更好的性能与易用性
 * @author pdc
 */
public class HessianSerializer implements ISerializer {

    public byte[] serialize(Object obj) {
        if (obj == null) {
            throw new NullPointerException();
        }
        ByteArrayOutputStream os = null;
        HessianOutput ho = null;
        try {
            os = new ByteArrayOutputStream();
            ho = new HessianOutput(os);
            ho.writeObject(obj);
            return os.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                ho.close();
                os.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public <T> T deserialize(byte[] data, Class<T> clazz) {
        if (data == null) {
            throw new NullPointerException();
        }
        ByteArrayInputStream is = null;
        HessianInput hi = null;
        try {
            is = new ByteArrayInputStream(data);
            hi = new HessianInput(is);
            return (T) hi.readObject();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                hi.close();
                is.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
