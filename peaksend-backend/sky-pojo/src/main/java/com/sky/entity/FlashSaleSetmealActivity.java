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
public class FlashSaleSetmealActivity implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long setmealId;

    private String activityName;

    private String setmealName;

    private BigDecimal setmealPrice;

    private String setmealImage;

    private Integer stock;

    private Integer status;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Long createUser;

    private Long updateUser;
}
