package com.pdc.im.processor;

import com.alibaba.fastjson.JSONObject;
import com.pdc.im.code.IMDecoder;
import com.pdc.im.code.IMEncoder;
import com.pdc.im.entity.IMMessage;
import com.pdc.im.protocol.IMProtocol;
import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.GlobalEventExecutor;

/**
 * 主要用于自定义协议内容的逻辑处理
 * 服务端的处理器
 *
 */
public class IMServerProcessor {
    /**
     * 记录在线用户,ChannelGroup类似上下文，即Tomcat的Applicaiton
     */
	private ChannelGroup onlineUsers = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    /**
     * 定义一些绑定在Channel(即client)上的扩展属性；
     * JSONObject里含有Map<String, Object> map，可用来做key：value操作，
     * 这样就达到了又能拿到属性，又能存储键值对的目的，类似redis的hash
     *
     */
	private final AttributeKey<String> NICK_NAME = AttributeKey.valueOf("nickName");
	private final AttributeKey<String> IP_ADDR = AttributeKey.valueOf("ipAddr");
	private final AttributeKey<JSONObject> ATTRS = AttributeKey.valueOf("attrs");
	//自定义解码器
	private IMDecoder decoder = new IMDecoder();
	//自定义编码器
	private IMEncoder encoder = new IMEncoder();

    /**
     * 发送消息
     * @param client
     * @param msg
     */
    public void handleMessage(Channel client, IMMessage msg){
        handleMessage(client,encoder.encode(msg));
    }

	/**
	 * 发送消息
	 * @param client
	 * @param msg
	 */
	public void handleMessage(Channel client, String msg){
		IMMessage requestAndResponse = decoder.decode(msg);
		if(null == requestAndResponse) return;
		
		String addr = getAddress(client);
		//登录
		if(requestAndResponse.getCmd().equals(IMProtocol.LOGIN.getName())){
            handleLogin(client, requestAndResponse, addr);
        }
		//聊天
		else if(requestAndResponse.getCmd().equals(IMProtocol.CHAT.getName())){
            handleChat(client, requestAndResponse);
        }
		//送鲜花
		else if(requestAndResponse.getCmd().equals(IMProtocol.FLOWER.getName())){
            handleFlower(client, requestAndResponse);
        }
	}

    /**
     * 送花
     * 客户端发送送花命令,前端设置好协议，请求后端
     * 后端获取自定义属性,判断是否能刷花，即60秒之内不允许重复刷鲜花
     * @param client
     * @param requestAndResponse
     */
    private void handleFlower(Channel client, IMMessage requestAndResponse) {
        JSONObject attrs = getAttrs(client);
        long currTime = sysTime();
        if(null != attrs){
            long lastTime = attrs.getLongValue("lastFlowerTime");
            //60秒之内不允许重复刷鲜花
            int secends = 60;
            long sub = currTime - lastTime;
            if(sub < 1000 * secends){
                requestAndResponse.setSender("you");
                requestAndResponse.setCmd(IMProtocol.SYSTEM.getName());
                requestAndResponse.setContent("您送鲜花太频繁," + (secends - Math.round(sub / 1000)) + "秒后再试");
                sendToBrowser(requestAndResponse, client);
                return;
            }
        }
        //正常送花
        for (Channel channel : onlineUsers) {
            if (channel == client) {
                requestAndResponse.setSender("you");
                requestAndResponse.setContent("你给大家送了一波鲜花雨");
                //设置最后一次发送鲜花的时间
                setAttrs(client, "lastFlowerTime", currTime);
            }else{
                requestAndResponse.setSender(getNickName(client));
                requestAndResponse.setContent(getNickName(client) + "送来一波鲜花雨");
            }
            requestAndResponse.setTime(sysTime());

            sendToBrowser(requestAndResponse, channel);
        }
    }

    private void sendToBrowser(IMMessage requestAndResponse, Channel channel) {
        String content = encoder.encode(requestAndResponse);
        channel.writeAndFlush(new TextWebSocketFrame(content));
    }

    /**
     * 聊天，包括文字和表情
     * 流程：依次判断发消息的人与用户的关系
     * 如果发消息的人是本身，则将名称改为自己
     * 如果发消息的人不是本身，则将名称设置为发消息的人
     * 最后发送到聊天面板上
     * @param client
     * @param requestAndResponse
     */
    private void handleChat(Channel client, IMMessage requestAndResponse) {
        //遍历所有用户
        for (Channel channel : onlineUsers) {
            if (channel == client) {
                requestAndResponse.setSender("大帅比自己");
            }else{
                requestAndResponse.setSender(getNickName(client));
            }
            requestAndResponse.setTime(sysTime());
            sendToBrowser(requestAndResponse, channel);
        }
    }

    /**
     * 登录
     * 如果不是本身，则通知用户有人加入
     * 如果是本身，则告知自己已加入
     * @param client
     * @param requestAndResponse
     * @param addr
     */
    private void handleLogin(Channel client, IMMessage requestAndResponse, String addr) {
        client.attr(NICK_NAME).getAndSet(requestAndResponse.getSender());
        client.attr(IP_ADDR).getAndSet(addr);
        onlineUsers.add(client);

        for (Channel channel : onlineUsers) {
            if(channel != client){
                requestAndResponse = new IMMessage(IMProtocol.SYSTEM.getName(), sysTime(), onlineUsers.size(), getNickName(client) + "加入聊天");
            }else{
                requestAndResponse = new IMMessage(IMProtocol.SYSTEM.getName(), sysTime(), onlineUsers.size(), "已与服务器建立连接！");
            }
            sendToBrowser(requestAndResponse, channel);
        }
    }

    /**
	 * 获取系统时间
	 * @return
	 */
	private Long sysTime(){
		return System.currentTimeMillis();
	}
    /**
     * 获取用户昵称
     * @param client
     * @return
     */
    public String getNickName(Channel client){
        return client.attr(NICK_NAME).get();
    }
    /**
     * 获取用户远程IP地址
     * @param client
     * @return
     */
    public String getAddress(Channel client){
        return client.remoteAddress().toString().replaceFirst("/","");
    }

    /**
     * 获取扩展属性
     * @param client
     * @return
     */
    public JSONObject getAttrs(Channel client){
        try{
            return client.attr(ATTRS).get();
        }catch(Exception e){
            return null;
        }
    }

    /**
     * 获取扩展属性
     * @param client
     * @return
     */
    private void setAttrs(Channel client,String key,Object value){
        try{
            JSONObject json = client.attr(ATTRS).get();
            json.put(key, value);
            client.attr(ATTRS).set(json);
        }catch(Exception e){
            JSONObject json = new JSONObject();
            json.put(key, value);
            client.attr(ATTRS).set(json);
        }
    }

    /**
     * 登出通知
     * @param client
     */
    public void logout(Channel client){
        //如果nickName为null，没有遵从聊天协议的连接，表示未非法登录
        if(getNickName(client) == null){ return; }
        for (Channel channel : onlineUsers) {
            IMMessage requestAndResponse = new IMMessage(IMProtocol.SYSTEM.getName(), sysTime(), onlineUsers.size(), getNickName(client) + "离开");
            sendToBrowser(requestAndResponse, channel);
        }
        onlineUsers.remove(client);
    }
}
