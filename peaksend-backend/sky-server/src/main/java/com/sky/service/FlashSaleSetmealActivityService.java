package com.sky.service;

import com.sky.dto.FlashSaleSetmealActivityDTO;
import com.sky.entity.FlashSaleSetmealActivity;
import com.sky.entity.FlashSaleSetmealOrder;
import com.sky.vo.FlashSaleSetmealSeizeVO;

import java.util.List;

public interface FlashSaleSetmealActivityService {

    void create(FlashSaleSetmealActivityDTO activityDTO);

    List<FlashSaleSetmealActivity> listAll();

    List<FlashSaleSetmealActivity> listAvailable();

    void updateStatus(Integer status, Long id);

    void delete(Long id);

    FlashSaleSetmealSeizeVO seize(Long activityId);

    List<FlashSaleSetmealOrder> listCurrentUserOrders();
}
