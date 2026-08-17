package com.sky.mq.publisher;

import java.time.LocalDateTime;

public interface FlashSaleOrderCreateMessagePublisher {

    boolean supportAsyncPersistence();

    void send(Long activityId, Long userId, String orderNo, LocalDateTime reservedTime);
}
