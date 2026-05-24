package com.sky.controller.user;

import com.sky.constant.StatusConstant;
import com.sky.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端店铺相关接口
 */
@RestController("userShopController") // 因为后面用户端也有同名 ShopController ，所以这里给了一个单独的 Bean 名，避免 Spring 容器里重名冲突
@RequestMapping("/user/shop")
@Api(tags = "店铺相关接口")
@Slf4j
public class ShopController {

    private static final String SHOP_STATUS_KEY = "SHOP_STATUS";

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 获取店铺营业状态
     *
     * @return
     */
    @GetMapping("/status")
    @ApiOperation("获取店铺的营业状态")
    public Result<Integer> getStatus() {
        Integer status = (Integer) redisTemplate.opsForValue().get(SHOP_STATUS_KEY);
        if (status == null) {
            status = StatusConstant.DISABLE;
        }
        log.info("用户端获取店铺营业状态：{}", StatusConstant.ENABLE.equals(status) ? "营业中" : "打烊中");
        return Result.success(status);
        //- 管理端和用户端虽然是两个 Controller
        //- 但它们读的是 Redis 里的同一份状态

    }
}
