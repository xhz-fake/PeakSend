package com.sky.controller.user;

import com.sky.constant.StatusConstant;
import com.sky.entity.Dish;
import com.sky.result.Result;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户端菜品浏览接口
 */
@RestController("userDishController")// 面试考点： 这里显式指定了Spring Bean的名字，避免了 Spring 容器层面的冲突
// 这个注解本身就包含了“把这个类交给 Spring 管理”的意思。

@RequestMapping("/user/dish")
@Slf4j
@Api(tags = "用户端菜品浏览接口")
public class DishController {

    @Autowired
    private DishService dishService;

    /**
     * 根据分类 id 查询起售中的菜品
     *
     * @param categoryId
     * @return
     */
    @GetMapping("/list")
    @ApiOperation("根据分类id查询菜品")
    @Cacheable(cacheNames = "dishCache", key = "#categoryId")
    //- 先去 Redis 里找 dishCache::16  [鮰鱼2斤, 江团鱼2斤, 草鱼2斤 + 各自口味]
    //- 如果找到了：直接返回, 不进方法体, 不查数据库
    //- 如果没找到：才进入方法体, 才会打印, 用户端根据分类查询菜品: 才会去查 DishMapper 和 DishFlavorMapper
    //- 方法返回之后，Spring Cache 自动把返回结果按 key 写入缓存

    public Result<List<DishVO>> list(@RequestParam Long categoryId) {// 从请求传入的参数中取值
        Dish dish = new Dish();//- 创建一个查询条件对象
        //- 它不是最终返回给前端的结果，而是后面查数据库的条件载体

        dish.setCategoryId(categoryId);// 把当前请求传进来的分类 id 放进去

        dish.setStatus(StatusConstant.ENABLE);// 设置了查询条件：必须是起售中的
        //- 它说明用户端不是查“所有菜品”，而是只查“起售中的菜品”
        //- 所以用户端看不到停售菜品，不是前端过滤，而是后端查询条件本来就限制了

        log.info("用户端根据分类在数据库中查询菜品：categoryId={}", categoryId);
        //  它可以帮你判断：
        //- 如果出现了，说明这次进了方法体，通常意味着 没命中缓存
        //- 如果没出现，说明大概率已经 直接命中缓存

        return Result.success(dishService.listWithFlavor(dish));
        //- 第一次没命中缓存后，不是方法体中的某一句写入缓存，而是 @Cacheable 在方法成功返回后，由 Spring Cache 自动把返回值放进缓存。
        //- 也就是说，真正被缓存的是这句的返回结果， 但注意：不是这句代码自己“主动调用 Redis 去 set”，而是它 把返回值交给了 Spring
        //- 然后 Spring 因为看到了 @Cacheable ，才自动帮你写入缓存
    }
}
