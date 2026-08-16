package com.sky.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class FlashSaleSetmealActivityDTO implements Serializable {

    private Long setmealId;

    private String activityName;

    private Integer stock;

    private Integer status;

    private LocalDateTime startTime;

    private LocalDateTime endTime;
}
