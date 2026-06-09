package com.sky.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ServerEndpoint("/ws/{sid}") //- “我要暴露一个 WebSocket 连接入口”，浏览器或前端页面以后可以连这个地址，连上以后，后端就能和这个页面持续通信
//{sid} 就说明：
//- 前端连接 WebSocket 时，路径里会带一个客户端标识
//- 这个标识后面就会传到 onOpen() 方法里
@Slf4j
public class WebSocketServer {

    private static final Map<String, Session> SESSION_MAP = new ConcurrentHashMap<>();
    //这个表是在记：哪一个客户端标识对应哪一条真实可通信的连接
    //如果不是 static ，那每次对象状态就容易不统一，后面广播时就找不到完整的在线连接集合。
    //ConcurrentHashMap 因为可能有多个管理端同时打开页面、同时断开、同时收消息。

    @OnOpen// - 浏览器或前端页面一旦连上 /ws/{sid}，WebSocket 框架自己就会触发这里
    public void onOpen(Session session, @PathParam("sid") String sid) {
        // sid -> 这台前端页面对应的连接
        // Session 就是“后端手里拿到的这条 WebSocket 连接对象”
        // 可以把它理解成：后端手里真正拿到了一根“和这个前端页面通信的线”。
        SESSION_MAP.put(sid, session);//把刚连进来的这个客户端，登记到在线连接表里。
        log.info("WebSocket connected: sid={}, currentOnline={}", sid, SESSION_MAP.size());
    }

    @OnMessage//收到客户端发来的 WebSocket 消息时自动触发
    public void onMessage(String message, @PathParam("sid") String sid) {
        log.info("WebSocket message received: sid={}, message={}", sid, message);
        //在我们当前 Day10 这条链里，最关键的是：
        //- 后端 -> 前端 的主动推送
        //所以这个 onMessage() 暂时没有承担主业务。
    }

    @OnClose// - 当前端页面关闭或者 WebSocket 连接断掉, WebSocket 框架就会自动触发这个方法
    public void onClose(@PathParam("sid") String sid) {// 连接断开的时候，后端至少知道：“断开的是谁”
        SESSION_MAP.remove(sid);// 把这个已经断开的客户端，从在线连接表里删掉
        log.info("WebSocket disconnected: sid={}, currentOnline={}", sid, SESSION_MAP.size());
    }

    public void sendToAllClient(String message) {//把一条消息发给所有在线客户端
        SESSION_MAP.forEach((sid, session) -> {// 把当前所有已经登记过的在线连接，一个个拿出来处理
            if (session == null || !session.isOpen()) {
                SESSION_MAP.remove(sid);// 如果发现这条连接无效了，就顺手把它从在线表里删掉
                return;
            }
            try {
                session.getBasicRemote().sendText(message);// 通过这条 WebSocket 连接，把文本消息推给前端
            } catch (IOException e) {
                log.error("WebSocket push failed: sid={}", sid, e);
            }
        });
        //WebSocket最核心的意义：
        //- 管理端不用一直轮询问“有没有新催单”
        //- 后端一有消息，直接推过去
    }
}
