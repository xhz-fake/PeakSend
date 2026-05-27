package com.sky.controller.user;

import com.sky.constant.JwtClaimsConstant;
import com.sky.dto.UserLoginDTO;
import com.sky.entity.User;
import com.sky.properties.JwtProperties;
import com.sky.result.Result;
import com.sky.service.UserService;
import com.sky.utils.JwtUtil;
import com.sky.vo.UserLoginVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * C端用户相关接口
 */
@RestController
@RequestMapping("/user/user")
@Api(tags = "C端用户相关接口")
@Slf4j
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtProperties jwtProperties;
    //JWT 本质上就是 后端发给前端的登录通行证

    /**
     * 微信登录
     *
     * @param userLoginDTO
     * @return
     */
    @PostMapping("/login")
    @ApiOperation("微信登录")// 主要作用是让接口文档里显示“微信登录”
    public Result<UserLoginVO> login(@RequestBody UserLoginDTO userLoginDTO) {
        log.info("微信用户登录：{}", userLoginDTO.getCode());

        User user = userService.wxLogin(userLoginDTO);
        //- Controller 自己不处理真正的登录细节
        //- 它把“微信登录到底怎么做”交给了 Service

        Map<String, Object> claims = new HashMap<>();// 准备一个 claims 你可以把它理解成： token 里要装的数据
        claims.put(JwtClaimsConstant.USER_ID, user.getId());// 等价于: claims.put("userId", user.getId());
        //- 登录成功后，把当前登录用户的 id 放进 token
        //- 注意，放进去的是 user.getId()
        //- 不是 openid
        //- 也不是 code
        //- 后面系统识别当前登录用户时，最终拿的是这个本地用户表里的 id

        String token = JwtUtil.createJWT(// 用密钥、有效期、载荷数据，生成真正的 token
                jwtProperties.getUserSecretKey(),
                jwtProperties.getUserTtl(),
                claims
                //- userSecretKey ：用户端 token 的密钥
                //- userTtl ：用户端 token 的有效期
                //- claims ：也就是刚才放进去的 userId
        );

        UserLoginVO userLoginVO = UserLoginVO.builder()
                .id(user.getId())
                .openid(user.getOpenid())// 返回这个微信用户的 openid
                .token(token)// 返回刚刚生成好的 token
                .deliveryFee(BigDecimal.ZERO)// 返回配送费，先给默认值 0
                .shopName("")// 返回店铺名，先给空字符串
                .shopAddress("")
                .shopId("")
                .build();// 现在正式构建出这个对象

        return Result.success(userLoginVO);// 是把这份业务数据包装成统一响应格式
    }
}
