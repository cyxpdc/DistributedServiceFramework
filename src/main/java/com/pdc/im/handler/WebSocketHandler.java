package com.pdc.im.handler;

import com.pdc.im.processor.IMServerProcessor;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.apache.log4j.Logger;

/**
 * MyImServer中addLast了此Handler
 * TextWebSocketFrame定义了和前端WebSocket的交互
 * author PDC
 */
public class WebSocketHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

	private static Logger LOG = Logger.getLogger(WebSocketHandler.class);
	
	private IMServerProcessor processor = new IMServerProcessor();

	/**
	 * TextWebSocketFrame能收到前端传来的数据
	 */
	@Override
	protected void channelRead0(ChannelHandlerContext ctx,TextWebSocketFrame msg) throws Exception {
        //System.out.println(msg.text());
	    processor.handleMessage(ctx.channel(), msg.text());
	}

	@Override
	public void handlerAdded(ChannelHandlerContext ctx) throws Exception { // (2)
		Channel client = ctx.channel();
		String addr = processor.getAddress(client);
		LOG.info("WebSocket Client:" + addr + "加入");
	}

	/**
	 * 用户通过非正常方式退出时，类似关闭钩子
	 * @param ctx
	 * @throws Exception
	 */
	@Override
	public void handlerRemoved(ChannelHandlerContext ctx) throws Exception { // (3)
		Channel client = ctx.channel();
		processor.logout(client);
		LOG.info("WebSocket Client:" + processor.getNickName(client) + "离开");
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception { // (5)
		Channel client = ctx.channel();
		String addr = processor.getAddress(client);
		LOG.info("WebSocket Client:" + addr + "上线");
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception { // (6)
		Channel client = ctx.channel();
		String addr = processor.getAddress(client);
		LOG.info("WebSocket Client:" + addr + "掉线");
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
			throws Exception {
		Channel client = ctx.channel();
		String addr = processor.getAddress(client);
		LOG.info("WebSocket Client:" + addr + "异常");
		// 当出现异常就关闭连接
		cause.printStackTrace();
		ctx.close();
	}

}
