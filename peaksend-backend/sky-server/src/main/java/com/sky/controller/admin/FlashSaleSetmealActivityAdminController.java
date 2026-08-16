package com.sky.controller.admin;

import com.sky.dto.FlashSaleSetmealActivityDTO;
import com.sky.entity.FlashSaleSetmealActivity;
import com.sky.result.Result;
import com.sky.service.FlashSaleSetmealActivityService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/flashSaleSetmealActivity")
@Api(tags = "管理端限量套餐活动接口")
@Slf4j
public class FlashSaleSetmealActivityAdminController {

    @Autowired
    private FlashSaleSetmealActivityService flashSaleSetmealActivityService;

    @PostMapping
    @ApiOperation("创建限量套餐活动")
    public Result<String> create(@RequestBody FlashSaleSetmealActivityDTO activityDTO) {
        log.info("创建限量套餐活动：{}", activityDTO);
        flashSaleSetmealActivityService.create(activityDTO);
        return Result.success();
    }

    @GetMapping("/list")
    @ApiOperation("查询全部限量套餐活动")
    public Result<List<FlashSaleSetmealActivity>> listAll() {
        return Result.success(flashSaleSetmealActivityService.listAll());
    }

    @PostMapping("/status/{status}")
    @ApiOperation("启停限量套餐活动")
    public Result<String> updateStatus(@PathVariable Integer status, @RequestParam Long id) {
        log.info("启停限量套餐活动：status={}, id={}", status, id);
        flashSaleSetmealActivityService.updateStatus(status, id);
        return Result.success();
    }

    @DeleteMapping
    @ApiOperation("删除限量套餐活动")
    public Result<String> delete(@RequestParam Long id) {
        log.info("删除限量套餐活动：id={}", id);
        flashSaleSetmealActivityService.delete(id);
        return Result.success();
    }
}
