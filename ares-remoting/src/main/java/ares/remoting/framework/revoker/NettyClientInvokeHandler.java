package ares.remoting.framework.revoker;

import ares.remoting.framework.model.AresResponse;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

/**
 * 客户端业务逻辑处理
 * @author pdc
 */
public class NettyClientInvokeHandler extends SimpleChannelInboundHandler<AresResponse> {

    /**
     * 服务端响应请求返回数据的时候会自动调用该方法
     * 我们通过实现该方法来接收服务端返回的数据,并实现客户端调用的业务逻辑：
     * 将Netty异步返回的结果存入阻塞队列,以便调用端同步获取
     * @param channelHandlerContext
     * @param response
     * @throws Exception
     */
    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, AresResponse response) throws Exception {
        RevokerResponseHolder.putResultValue(response);
        //RevokerProxyBeanFactory.doReceived(response);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }
}
