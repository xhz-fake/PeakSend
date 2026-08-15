package com.sky.controller.user;

import com.sky.entity.Category;
import com.sky.result.Result;
import com.sky.service.CategoryService;
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
 * 用户端分类相关接口
 */
@RestController("userCategoryController")
@RequestMapping("/user/category")
@Api(tags = "用户端分类相关接口")
@Slf4j
public class CategoryController {

    @Autowired
    private CategoryService categoryService;

    /**
     * 根据类型查询分类
     *
     * @param type
     * @return
     */
    @GetMapping("/list")
    @ApiOperation("根据类型查询分类")
    @Cacheable(cacheNames = "categoryCache", key = "#type == null ? 'all' : #type")
    //先去缓存里看看有没有现成结果，有就直接返回，没有再执行方法体。
    public Result<List<Category>> list(@RequestParam(required = false) Integer type) {
        log.info("用户端根据类型查询分类：{}", type);
        return Result.success(categoryService.list(type));
    }
    //当前端请求“给我分类列表”时，后端进入这个方法，
    // 然后调用 categoryService.list(type) 去查分类数据，拿到数据库结果后再写入 Redis;
    // 最后把结果返回给前端。
}
