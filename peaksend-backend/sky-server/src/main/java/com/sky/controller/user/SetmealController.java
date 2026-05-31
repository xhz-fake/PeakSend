package com.sky.controller.user;

import com.sky.constant.StatusConstant;
import com.sky.entity.Setmeal;
import com.sky.result.Result;
import com.sky.service.SetmealService;
import com.sky.vo.DishItemVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户端套餐浏览接口
 */
@RestController("userSetmealController")
@RequestMapping("/user/setmeal")
@Api(tags = "用户端套餐浏览接口")
@Slf4j
public class SetmealController {

    @Autowired // 从 Spring 容器里拿一个已经创建好的 Bean 来用
    private SetmealService setmealService;

    /**
     * 根据分类 id 查询起售中的套餐
     *
     * @param categoryId
     * @return
     */
    @GetMapping("/list")
    @ApiOperation("根据分类id查询套餐")
    @Cacheable(cacheNames = "setmealCache", key = "#categoryId")
    public Result<List<Setmeal>> list(@RequestParam Long categoryId) {
        Setmeal setmeal = new Setmeal();
        setmeal.setCategoryId(categoryId);
        setmeal.setStatus(StatusConstant.ENABLE);
        log.info("用户端根据分类查询套餐：categoryId={}", categoryId);
        return Result.success(setmealService.list(setmeal));
    }

    /**
     * 根据套餐 id 查询包含的菜品
     *
     * @param id
     * @return
     */
    @GetMapping("/dish/{id}")
    @ApiOperation("根据套餐id查询包含的菜品")
    public Result<List<DishItemVO>> dishList(@PathVariable Long id) {
        log.info("用户端根据套餐id查询菜品项：{}", id);
        return Result.success(setmealService.getDishItemById(id));
    }
}
