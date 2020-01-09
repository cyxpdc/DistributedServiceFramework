package com.pdc.im.code;

import com.pdc.im.entity.IMMessage;
import com.pdc.im.protocol.IMProtocol;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.msgpack.MessagePack;
import org.msgpack.MessageTypeException;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自定义IM协议的编码器
 * @author pdc
 */
public class IMDecoder extends ByteToMessageDecoder {

	//解析IM写一下请求内容的正则
	private Pattern pattern = Pattern.compile("^\\[(.*)\\](\\s\\-\\s(.*))?");

	/**
	 * 将字符串变成Java对象
	 * 即反序列化的过程
	 * @param ctx
	 * @param in
	 * @param out
	 * @throws Exception
	 */
	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf in,List<Object> out) throws Exception {
		/*try{
			//先获取可读字节数
	        final int length = in.readableBytes();
	        final byte[] array = new byte[length];
            //变为字符串(in.readableBytes)
	        String content = new String(array,in.readerIndex(),length);
	        //空消息、不符合协议则不解析
	        if(null == content || "".equals(content.trim())) return ;
            if(!IMProtocol.isIMProtocol(content)){
                ctx.channel().pipeline().remove(this);
                return;
            }
	        //反序列化
            //首先把字节转化为MessagePack，然后再转为IMMessage
	        in.getBytes(in.readerIndex(), array, 0, length);
	        out.add(new MessagePack().read(array, IMMessage.class));
	        in.clear();
		}catch(MessageTypeException e){
			ctx.channel().pipeline().remove(this);
		}*/
	}
	
	/**
	 * 字符串解析成自定义即时通信协议，封装在IMMessage中
	 * @param msg
	 * @return
	 */
	public IMMessage decode(String msg){
		if(null == msg || "".equals(msg.trim())){ return null; }
		try{
			Matcher m = pattern.matcher(msg);
			String header = "";
			String content = "";
			if(m.matches()){
				header = m.group(1);
				content = m.group(3);
			}
			
			String [] heards = header.split("\\]\\[");
			long time = 0;
			try{ time = Long.parseLong(heards[1]); } catch(Exception e){}
			String nickName = heards[2];
			//昵称最多十个字
			nickName = nickName.length() < 10 ? nickName : nickName.substring(0, 9);
			
			if(msg.startsWith("[" + IMProtocol.LOGIN.getName() + "]")){
				return new IMMessage(heards[0],time,nickName);
			}else if(msg.startsWith("[" + IMProtocol.CHAT.getName() + "]")){
				return new IMMessage(heards[0],time,nickName,content);
			}else if(msg.startsWith("[" + IMProtocol.FLOWER.getName() + "]")){
				return new IMMessage(heards[0],time,nickName);
			}else{
				return null;
			}
		}catch(Exception e){
			e.printStackTrace();
			return null;
		}
	}
}
