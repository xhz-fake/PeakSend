package com.sky.interceptor;

import com.sky.constant.JwtClaimsConstant;
import com.sky.context.BaseContext;
import com.sky.properties.JwtProperties;
import com.sky.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 用户端 jwt 令牌校验拦截器
 */
@Component // 告诉 Spring：把这个类也注册到容器里
@Slf4j
public class JwtTokenUserInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtProperties jwtProperties;// 它本质上是：Spring 把 application.yml 里 sky.jwt 这组配置绑定成一个 JwtProperties 对象，再把这个对象放进 Spring 容器，最后通过 @Autowired 注入到拦截器里
    //- jwtProperties 就像一本“JWT 配置说明书”
    //- 里面记着：
    //- 用户端 token 头名字叫什么
    //- 用户端密钥是什么
    //- token 过期时间是多少

    /**
     * 校验 jwt
     *
     * @param request
     * @param response
     * @param handler
     * @return
     * @throws Exception
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //- preHandle() 是被 Spring MVC 在请求到来时自动回调执行的
        //- 1.前端发一个请求，比如 /user/shoppingCart/list
        //- 2.Spring MVC 先收到这个请求
        //- 3.Spring 发现：这个路径匹配了 /user/**
        //- 4.Spring 又发现：这个路径不在排除名单里
        //- 5.所以 Spring 自动调用这个拦截器的 preHandle()
        //- 6.如果 preHandle() 返回 true ，请求继续进入 Controller
        //- 7.如果返回 false ，请求直接被拦下

        // 这里的 request 不是你自己 new 的，也不是全局变量，而是：Spring MVC 在调用 preHandle(...) 时传进来的,是当前这次 HTTP 请求的完整信息对象
        //
        //  里面就装着：
        //- 1.请求头
        //- 2.请求路径
        //- 3.查询参数
        //- 4.请求方法
        //- 5.body 等信息
        //
        //  它的来源是：
        //- 浏览器 / 小程序 / APIFox 发了一个 HTTP 请求
        //- Tomcat 先收到这个请求
        //- Spring MVC 继续处理这个请求
        //- Spring 自动调用 preHandle(...)时，把“当前这次请求对象”传给参数 request

        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        String token = request.getHeader(jwtProperties.getUserTokenName());// 先从请求头里拿 token，这里取的是配置里的请求头名字，在你这个项目里就是 authentication

        try {
            log.info("用户端 jwt 校验:{}", token);
            Claims claims = JwtUtil.parseJWT(jwtProperties.getUserSecretKey(), token);// 用后端配置好的密钥去解析这个 token
            Long userId = Long.valueOf(claims.get(JwtClaimsConstant.USER_ID).toString());// 从 token 里把之前放进去的 userId 取出来
            BaseContext.setCurrentId(userId);// 这一句特别关键：把“当前登录用户的 id”存到当前线程上下文里，
            // BaseContext 只是后端在“本次请求处理过程中”临时共享 userId 的地方（Controller、Service、Mapper 这一整条链都能随时拿到“当前用户是谁）
            // 下一个新请求来了，还是要重新从 token 里解析一次，再重新 setCurrentId(...)

            return true;// 说明鉴权通过，这个请求可以继续往 Controller 和 Service 走
        } catch (Exception ex) {
            response.setStatus(401);// 如果 token 有问题，比如没传、过期、签名不对，就直接拦下来，返回 401
            return false;
        }
        //前面登录链做那么多事，最后就是为了这类业务代码能很自然地拿到：
        //- 当前用户是谁
        //- 当前用户要加的是哪道菜
        //- 当前用户选的是什么口味
    }
}
