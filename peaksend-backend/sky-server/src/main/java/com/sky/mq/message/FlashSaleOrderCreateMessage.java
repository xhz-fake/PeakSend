package com.sky.mq.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlashSaleOrderCreateMessage implements Serializable {

    private Long activityId;
    private Long userId;
    private String orderNo;
    private LocalDateTime reservedTime;
}
