package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlashSaleSetmealOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long activityId;

    private Long userId;

    private Long setmealId;

    private String orderNo;

    private String activityName;

    private String setmealName;

    private BigDecimal setmealPrice;

    private String setmealImage;

    private Integer status;

    private LocalDateTime createTime;
}
