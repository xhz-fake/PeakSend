package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sky.constant.MessageConstant;
import com.sky.dto.UserLoginDTO;
import com.sky.entity.User;
import com.sky.exception.LoginFailedException;
import com.sky.mapper.UserMapper;
import com.sky.properties.WeChatProperties;
import com.sky.service.UserService;
import com.sky.utils.HttpClientUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * C端用户业务实现
 */
@Service
@Slf4j
public class UserServiceImpl implements UserService {

    private static final String WX_LOGIN = "https://api.weixin.qq.com/sns/jscode2session";

    @Autowired
    private WeChatProperties weChatProperties;

    @Autowired
    private UserMapper userMapper;

    /**
     * 微信登录
     *
     * @param userLoginDTO
     * @return
     */
    @Override
    public User wxLogin(UserLoginDTO userLoginDTO) {// 这是“微信登录”的核心业务方法，输入是前端传来的 code ，输出是我们系统里的 User 对象。
        String openid = getOpenid(userLoginDTO.getCode());// 先拿前端传来的 code ，去问微信，换回这个用户在当前小程序下对应的 openid 。

        if (openid == null || openid.isEmpty()) {// 先做一次兜底判断。
            throw new LoginFailedException(MessageConstant.LOGIN_FAILED);
        }

        User user = userMapper.getByOpenid(openid);//这就是“后端拿 openid 去查我们自己 user 表”的真实代码。
        if (user == null) {// 如果查出来是空，说明这个微信用户是第一次来我们系统。
            user = User.builder() // 先在 Java 里组装一个新的 User 对象
                    .openid(openid) // 把刚刚换回来的 openid 存进我们的用户表记录里。
                    .createTime(LocalDateTime.now())
                    .build();
            userMapper.insert(user); // 把这个新用户插入数据库。也可以理解成“自动注册”。
        }

        return user;// 最后不管是老用户还是新用户，都把本地这个 user 返回给上层。
    }

    /**
     * 调用微信接口服务，获取微信用户 openid
     *
     * @param code
     * @return
     */
    private String getOpenid(String code) {
        Map<String, String> map = new HashMap<>();
        map.put("appid", weChatProperties.getAppid());// 这行就是把我们小程序自己的 AppID 发给微信
        map.put("secret", weChatProperties.getSecret());// 这行是把这个小程序对应的密钥也一起发过去
        map.put("js_code", code);// 这行是把前端刚拿到的临时登录凭证 code 发过去
        map.put("grant_type", "authorization_code");

        String json = HttpClientUtil.doGet(WX_LOGIN, map);
        log.info("微信登录返回结果：{}", json);

        JSONObject jsonObject = JSON.parseObject(json);//后端从微信的 JSON 返回结果里里取出 openid
        return jsonObject.getString("openid");
    }
}
