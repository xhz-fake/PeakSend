package com.sky.mq.publisher;

import com.sky.constant.RocketMqTopicConstant;
import com.sky.mq.message.FlashSaleOrderCreateMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@ConditionalOnProperty(prefix = "sky.rocketmq", name = "flash-sale-async-enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class RocketMqFlashSaleOrderCreateMessagePublisher implements FlashSaleOrderCreateMessagePublisher {

    private final RocketMQTemplate rocketMQTemplate;

    @Override
    public boolean supportAsyncPersistence() {
        return true;
    }

    @Override
    public void send(Long activityId, Long userId, String orderNo, LocalDateTime reservedTime) {
        FlashSaleOrderCreateMessage message = FlashSaleOrderCreateMessage.builder()//它不是直接传一堆散参数，而是封装成 FlashSaleOrderCreateMessage
                .activityId(activityId)
                .userId(userId)
                .orderNo(orderNo)
                .reservedTime(reservedTime)
                .build();
                // 后面消费者异步落库就靠这几个值。

        rocketMQTemplate.syncSend(
                RocketMqTopicConstant.FLASH_SALE_ORDER_CREATE_TOPIC,// 发到指定 topic： FLASH_SALE_ORDER_CREATE_TOPIC
                MessageBuilder.withPayload(message).build(),
                3000
        );
        log.info("发送限量套餐异步落库消息成功：activityId={}, userId={}, orderNo={}", activityId, userId, orderNo);
    }
}
