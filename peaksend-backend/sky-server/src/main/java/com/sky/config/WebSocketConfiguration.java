package com.sky.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

@Configuration// 它是在告诉 Spring： 这是一个配置类
public class WebSocketConfiguration {

    @Bean// 把下面这个方法返回的对象，交给 Spring 管理。
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }
    //- 就是创建一个 ServerEndpointExporter 对象，并把它交给 Spring。他负责把 @ServerEndpoint 标注的类导出并注册成真正可用的 WebSocket 端点
}
