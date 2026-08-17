package com.sky.mq.publisher;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@ConditionalOnProperty(prefix = "sky.rocketmq", name = "flash-sale-async-enabled", havingValue = "false", matchIfMissing = true)
public class NoopFlashSaleOrderCreateMessagePublisher implements FlashSaleOrderCreateMessagePublisher {

    @Override
    public boolean supportAsyncPersistence() {
        return false;
    }

    @Override
    public void send(Long activityId, Long userId, String orderNo, LocalDateTime reservedTime) {
        // RocketMQ 关闭时保持 Day15 同步落库链路不变。
    }
}
