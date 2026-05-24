package com.sky.controller.admin;

import com.sky.constant.StatusConstant;
import com.sky.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 店铺营业状态相关接口
 */
@RestController("adminShopController") // - 这是一个接口控制器,返回 JSON,因为后面用户端也有同名 ShopController ，所以这里给了一个单独的 Bean 名，避免 Spring 容器里重名冲突
@RequestMapping("/admin/shop") // 说明这个类下面的接口都统一挂在 /admin/shop 下面
@Api(tags = "店铺相关接口") // 给接口文档分组用的
@Slf4j // 用来打日志
public class ShopController {

    private static final String SHOP_STATUS_KEY = "SHOP_STATUS";

    @Autowired
    private RedisTemplate redisTemplate;
    // - Spring IoC 帮我们注入了 RedisTemplate
    // - 后端后面就是通过这个对象去操作 Redis

    /**
     * 设置店铺营业状态
     *
     * @param status
     * @return
     */
    @PutMapping("/{status}") // PUT /admin/shop/{status}
    @ApiOperation("设置店铺的营业状态")
    public Result<String> setStatus(@PathVariable Integer status) {// 从路径里取参数
        log.info("设置店铺营业状态：{}", status != null && StatusConstant.ENABLE.equals(status) ? "营业中" : "打烊中");
        redisTemplate.opsForValue().set(SHOP_STATUS_KEY, status);
        //- 用 Redis 的字符串/值操作
        //- 把 key SHOP_STATUS
        //- 对应的 value 改成当前传进来的 status
        return Result.success();
    }

    /**
     * 获取店铺营业状态
     *
     * @return
     */
    @GetMapping("/status")
    @ApiOperation("获取店铺的营业状态")
    public Result<Integer> getStatus() {
        Integer status = (Integer) redisTemplate.opsForValue().get(SHOP_STATUS_KEY);
        //- 去 Redis 里拿 SHOP_STATUS
        //- 然后把它转成 Integer

        if (status == null) {
            status = StatusConstant.DISABLE;
        }
        //- 没读到值，就默认按：
        //  - 0
        //  - 也就是打烊中
        //这也是一种很真实的后端思路：
        //- 后端不能只假设中间件里一定有数据
        //- 要给初始状态一个明确兜底

        log.info("获取店铺营业状态：{}", StatusConstant.ENABLE.equals(status) ? "营业中" : "打烊中");
        return Result.success(status);
    }
}
