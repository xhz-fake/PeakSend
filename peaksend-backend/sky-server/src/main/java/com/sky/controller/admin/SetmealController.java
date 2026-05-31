package com.sky.controller.admin;

import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.SetmealService;
import com.sky.vo.SetmealVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 套餐管理
 */
@RestController // 这是一个接口控制器, 返回的通常是 JSON 数据
@RequestMapping("/admin/setmeal") // 这个类下面所有接口，都统一以 /admin/setmeal 开头
@Slf4j // 让这个类能方便打日志
@Api(tags = "套餐相关接口") // - 生成接口文档时更清楚, 让接口在文档页面里归到“套餐相关接口”这一组
public class SetmealController {// 这个类里写的方法，默认都是给前端接口用的。

    @Autowired// 依赖注入, Controller 只负责接请求，然后把动作交给 Service
    private SetmealService setmealService;

    /**
     * 新增套餐
     *
     * @param setmealDTO
     * @return
     */
    @PostMapping // 说明这是一个 POST 请求
    @ApiOperation("新增套餐")
    @CacheEvict(cacheNames = "setmealCache", key = "#setmealDTO.categoryId")
    public Result<String> save(@RequestBody SetmealDTO setmealDTO) { // - 前端传来的 JSON 请求体, 会被 Spring MVC 自动转换成一个 SetmealDTO 对象
        log.info("新增套餐：{}", setmealDTO);
        setmealService.saveWithDish(setmealDTO);
        return Result.success();
    }

    /**
     * 套餐分页查询
     *
     * @param setmealPageQueryDTO
     * @return
     */
    @GetMapping("/page")
    @ApiOperation("套餐分页查询")
    public Result<PageResult> page(SetmealPageQueryDTO setmealPageQueryDTO) {// 这里参数不是从 JSON 请求体里来的，而是从：URL 查询参数
        log.info("套餐分页查询：{}", setmealPageQueryDTO);
        PageResult pageResult = setmealService.pageQuery(setmealPageQueryDTO);
        return Result.success(pageResult);
    }

    /**
     * 套餐起售停售
     *
     * @param status
     * @param id
     * @return
     */
    @PostMapping("/status/{status}")
    @ApiOperation("套餐起售停售")
    @CacheEvict(cacheNames = "setmealCache", allEntries = true)
    public Result<String> startOrStop(@PathVariable Integer status, @RequestParam Long id) {//从 URL 路径里拿到参数的具体值
        // @RequestParam Long id 说明：id 是从查询参数里拿的
        // status 更像“这次动作本身的一部分”，所以放路径里可读性强
        // id 更像“这次要操作哪条数据的附加参数”，所以常放查询参数里
        log.info("套餐起售停售：status={}, id={}", status, id);
        setmealService.startOrStop(status, id);
        return Result.success();
    }

    /**
     * 批量删除套餐
     *
     * @param ids
     * @return
     */
    @org.springframework.web.bind.annotation.DeleteMapping
    @ApiOperation("批量删除套餐")// 支持批量删，以后如果要删多个套餐，理论上可以传：ids=32&ids=33
    @CacheEvict(cacheNames = "setmealCache", allEntries = true)
    public Result<String> delete(@RequestParam List<Long> ids) {
        log.info("批量删除套餐：{}", ids);
        setmealService.deleteBatch(ids);
        return Result.success();
    }

    /**
     * 根据id查询套餐
     *
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    @ApiOperation("根据id查询套餐")
    public Result<SetmealVO> getById(@PathVariable Long id) {
        log.info("根据id查询套餐：{}", id);
        SetmealVO setmealVO = setmealService.getByIdWithDish(id);// - 不是只按 id 查一条套餐主表， 还要把关联菜品一起查出来
        return Result.success(setmealVO);
    }

    /**
     * 修改套餐
     *
     * @param setmealDTO
     * @return
     */
    @PutMapping
    @ApiOperation("修改套餐")
    @CacheEvict(cacheNames = "setmealCache", allEntries = true)
    public Result<String> update(@RequestBody SetmealDTO setmealDTO) {
        log.info("修改套餐：{}", setmealDTO);
        setmealService.updateWithDish(setmealDTO);// - 这次修改不是只改套餐主表，还要一起处理套餐和菜品关系
        return Result.success();
    }
}
