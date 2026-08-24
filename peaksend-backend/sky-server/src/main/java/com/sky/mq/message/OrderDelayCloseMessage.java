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
public class OrderDelayCloseMessage implements Serializable {

    private String traceId;
    private Long orderId;
    private Long userId;
    private String orderNumber;
    private LocalDateTime orderTime;
}
