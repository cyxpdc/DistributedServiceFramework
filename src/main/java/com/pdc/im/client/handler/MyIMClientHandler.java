package com.pdc.im.client.handler;

import com.pdc.im.entity.IMMessage;
import com.pdc.im.protocol.IMProtocol;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.Scanner;

/**
 * 聊天客户端逻辑实现
 * @author pdc
 *
 */
public class MyIMClientHandler extends ChannelInboundHandlerAdapter{

	private static Logger LOG = Logger.getLogger(MyIMClientHandler.class);
	private ChannelHandlerContext ctx;
	private String nickName;
	public MyIMClientHandler(String nickName){
		this.nickName = nickName;
	}
	
	/**启动客户端控制台
     * 开启线程
     * 不断接收控制台的输入，然后发送到浏览器
     **/
    private void session() throws IOException {
    		new Thread(){
    			public void run(){
    				LOG.info(nickName + ",你好，请在控制台输入消息内容");
    				IMMessage message = null;
    		        Scanner scanner = new Scanner(System.in);
    		        do{
    			        	if(scanner.hasNext()){
    			        		String input = scanner.nextLine();
    			        		if("exit".equals(input)){
    			        			message = new IMMessage(IMProtocol.LOGOUT.getName(),System.currentTimeMillis(),nickName);
    			        		}else{
    			        			message = new IMMessage(IMProtocol.CHAT.getName(),System.currentTimeMillis(),nickName,input);
    			        		}
    			        	}
    		        }
    		        while (sendMsgToClient(message));
    		        scanner.close();
    			}
    		}.start();
    }
	
    /**
     * tcp链路建立成功后调用
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
    		this.ctx = ctx;
        IMMessage message = new IMMessage(IMProtocol.LOGIN.getName(),System.currentTimeMillis(),this.nickName);
        sendMsgToClient(message);
        LOG.info("成功连接服务器,已执行登录动作");
        session();
    }
    /**
     * 发送消息给客户端
     * @param msg
     * @return
     * @throws IOException 
     */
    private boolean sendMsgToClient(IMMessage msg){
        ctx.channel().writeAndFlush(msg);//发送到浏览器
        LOG.info("已发送至聊天面板,请继续输入");
        return msg.getCmd().equals(IMProtocol.LOGOUT) ? false : true;
    }
    /**
     * 收到消息后调用
     * @throws IOException 
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws IOException {
    	IMMessage m = (IMMessage)msg;
    	LOG.info(m);
    }
    /**
     * 发生异常时调用
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    	LOG.info("与服务器断开连接:"+cause.getMessage());
        ctx.close();
    }
}
