package com.sky.controller.user;

import com.sky.entity.FlashSaleSetmealActivity;
import com.sky.entity.FlashSaleSetmealOrder;
import com.sky.result.Result;
import com.sky.service.FlashSaleSetmealActivityService;
import com.sky.vo.FlashSaleSetmealSeizeVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController("userFlashSaleSetmealActivityController")
@RequestMapping("/user/flashSaleSetmealActivity")
@Api(tags = "用户端限量套餐活动接口")
@Slf4j
public class FlashSaleSetmealActivityController {

    @Autowired
    private FlashSaleSetmealActivityService flashSaleSetmealActivityService;

    @GetMapping("/list")
    @ApiOperation("查询当前可抢限量套餐活动")
    public Result<List<FlashSaleSetmealActivity>> listAvailable() {
        return Result.success(flashSaleSetmealActivityService.listAvailable());
    }

    @PostMapping("/seize/{activityId}")
    @ApiOperation("抢购限量套餐活动")
    public Result<FlashSaleSetmealSeizeVO> seize(@PathVariable Long activityId) {
        log.info("用户抢购限量套餐活动：activityId={}", activityId);
        return Result.success(flashSaleSetmealActivityService.seize(activityId));
    }

    @GetMapping("/orders")
    @ApiOperation("查询当前用户抢购记录")
    public Result<List<FlashSaleSetmealOrder>> listCurrentUserOrders() {
        return Result.success(flashSaleSetmealActivityService.listCurrentUserOrders());
    }
}
