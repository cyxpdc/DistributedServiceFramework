package com.pdc.im.code;

import com.pdc.im.entity.IMMessage;
import com.pdc.im.protocol.IMProtocol;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.msgpack.MessagePack;

/**
 * 自定义IM协议的编码器
 * MessageToByteEncoder,先序列化，就能传输了
 * netty内置对象都是如此，都经过了编码解码的过程
 * 因为IMMessage是自定义对象，所以同样也需要自定义编码解码器
 */
public class IMEncoder extends MessageToByteEncoder<IMMessage> {

    /**
     * 将对象转为字符串输出到浏览器
     * 即序列化的过程
     * @param ctx
     * @param msg
     * @param out
     * @throws Exception
     */
	@Override
	protected void encode(ChannelHandlerContext ctx, IMMessage msg, ByteBuf out)
			throws Exception {
	    /*if(msg != null) out.writeBytes(new MessagePack().write(msg));
        else System.out.println("msg为null");*/
	}

    /**
     * 编码，即解析IMMessage为String
     * @param msg
     * @return
     */
	public String encode(IMMessage msg){
		if(null == msg){ return ""; }
		String prex = "[" + msg.getCmd() + "]" + "[" + msg.getTime() + "]";
		if(IMProtocol.LOGIN.getName().equals(msg.getCmd()) ||
		   IMProtocol.CHAT.getName().equals(msg.getCmd()) ||
		   IMProtocol.FLOWER.getName().equals(msg.getCmd())){
			prex += ("[" + msg.getSender() + "]");
		}else if(IMProtocol.SYSTEM.getName().equals(msg.getCmd())){
			prex += ("[" + msg.getOnline() + "]");
		}
		if(!(null == msg.getContent() || "".equals(msg.getContent()))){
			prex += (" - " + msg.getContent());
		}
		return prex;
	}

}
