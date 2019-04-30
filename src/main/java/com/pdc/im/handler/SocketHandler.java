package com.pdc.im.handler;

import com.pdc.im.entity.IMMessage;
import com.pdc.im.processor.IMServerProcessor;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.apache.log4j.Logger;

/**
 * 本地协议，发送一个IMMessage对象，能直接在网页上接收，这样就能跨平台
 * 协议跑socket即可
 * author pdc
 */
public class SocketHandler extends SimpleChannelInboundHandler<IMMessage>{

	private static Logger LOG = Logger.getLogger(SocketHandler.class);
	
	private IMServerProcessor processor = new IMServerProcessor();

    /**
     * 网页发过来的字符串会自动变成IMMessage对象
     * 而这里发送的对象到了网页会自动变成字符串
     * 这也是RPC的基本原理
     * 同样，任务委托给IMProcessor
     * @param ctx
     * @param msg
     * @throws Exception
     */
	@Override
	protected void channelRead0(ChannelHandlerContext ctx, IMMessage msg) throws Exception {
		processor.handleMessage(ctx.channel(), msg);
	}
    
    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        LOG.info("用户创建...");
        super.handlerAdded(ctx);
    }
    
    @Override
	public void handlerRemoved(ChannelHandlerContext ctx) throws Exception { // (3)
		Channel client = ctx.channel();
		processor.logout(client);
		LOG.info("Socket Client:" + processor.getNickName(client) + "离开");
	}
    
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        LOG.info("channelInactive");
        super.channelInactive(ctx);
    }
    /**
     * tcp链路建立成功后调用
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        LOG.info("Socket Client: 有客户端连接："+ processor.getAddress(ctx.channel()));
    }


    /**
     * 异常处理
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOG.info("Socket Client: 与客户端断开连接:"+cause.getMessage());
        cause.printStackTrace();
        ctx.close();
    }

}
