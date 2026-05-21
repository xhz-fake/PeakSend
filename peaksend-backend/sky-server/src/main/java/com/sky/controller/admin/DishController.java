package com.sky.controller.admin;

import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 菜品管理
 */
@RestController// - 说明这是一个接口控制器，返回的通常是 JSON，不是跳转页面
@RequestMapping("/admin/dish")// 说明这个类下面所有接口，都以 /admin/dish 开头
@Slf4j// 说明这里可以方便打日志
@Api(tags = "菜品相关接口")// 这是给 Swagger / Knife4j 看的，方便生成接口文档
public class DishController {
// 一个模块的 Controller ，往往就是这个模块对外能力清单

    @Autowired// Controller 自己并不打算直接处理数据库，而是把真正的业务动作交给： DishServiceImpl
    private DishService dishService;
    // 这就是典型分层思想：

    /**
     * 新增菜品
     *
     * @param dishDTO
     * @return
     */
    @PostMapping   // 说明这是一个 POST 请求， - 所以完整地址就是：POST /admin/dish
    @ApiOperation("新增菜品")  // 这是接口文档描述
    public Result<String> save(@RequestBody DishDTO dishDTO) {  //- 它的意思是：前端传来的 JSON 请求体，要被转换成一个 DishDTO 对象
        log.info("新增菜品：{}", dishDTO);
        dishService.saveWithFlavor(dishDTO);
        return Result.success();
    }
    //标准的 Controller 风格：
    //- 入口清楚
    //- 参数清楚
    //- 不自己处理复杂业务

    /**
     * 根据id查询菜品
     *
     * @param id
     * @return
     */
    @GetMapping("/{id}") // 完整地址类似：GET /admin/dish/71
    @ApiOperation("根据id查询菜品")
    public Result<DishVO> getById(@PathVariable Long id) { // @PathVariable 的意思是：从 URL 路径里取值
        log.info("根据id查询菜品：{}", id);
        DishVO dishVO = dishService.getByIdWithFlavor(id);
        return Result.success(dishVO);
    }

    /**
     * 菜品分页查询
     *
     * @param dishPageQueryDTO
     * @return
     */
    @GetMapping("/page")
    @ApiOperation("菜品分页查询")
    public Result<PageResult> page(DishPageQueryDTO dishPageQueryDTO) {
        log.info("菜品分页查询：{}", dishPageQueryDTO);
        PageResult pageResult = dishService.pageQuery(dishPageQueryDTO);
        return Result.success(pageResult);
    }

    /**
     * 批量删除菜品
     *
     * @param ids
     * @return
     */
    @DeleteMapping
    @ApiOperation("批量删除菜品")
    public Result<String> delete(@RequestParam List<Long> ids) { //@RequestParam 它的意思是：从 URL 查询参数里取值
        log.info("批量删除菜品：{}", ids);
        dishService.deleteBatch(ids);
        return Result.success();
    }

    /**
     * 菜品起售停售
     *
     * @param status
     * @param id
     * @return
     */
    @PostMapping("/status/{status}")
    @ApiOperation("菜品起售停售")
    public Result<String> startOrStop(@PathVariable Integer status, @RequestParam Long id) {
        log.info("菜品起售停售：status={}, id={}", status, id);
        dishService.startOrStop(status, id);
        return Result.success();
    }
    //以后看到方法参数，脑子里要先问：
    //- 这是从 body 来的？
    //- 从路径来的？
    //- 从查询参数来的？

    /**
     * 修改菜品
     *
     * @param dishDTO
     * @return
     */
    @PutMapping
    @ApiOperation("修改菜品")
    public Result<String> update(@RequestBody DishDTO dishDTO) {
        log.info("修改菜品：{}", dishDTO);
        dishService.updateWithFlavor(dishDTO);
        return Result.success();
    }
}
