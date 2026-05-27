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
@Component
@Slf4j
public class JwtTokenUserInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtProperties jwtProperties;

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

        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        String token = request.getHeader(jwtProperties.getUserTokenName());// 先从请求头里拿 token，这里取的是配置里的请求头名字，在你这个项目里就是 authentication

        try {
            log.info("用户端 jwt 校验:{}", token);
            Claims claims = JwtUtil.parseJWT(jwtProperties.getUserSecretKey(), token);// 后端开始解析这个 token
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
