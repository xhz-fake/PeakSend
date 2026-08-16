package com.sky.vo;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@Builder
public class FlashSaleSetmealSeizeVO implements Serializable {

    private Long activityId;

    private Long setmealId;

    private String orderNo;
}
