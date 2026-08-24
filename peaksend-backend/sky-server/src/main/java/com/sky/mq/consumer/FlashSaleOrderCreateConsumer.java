package com.sky.mq.consumer;

import com.sky.constant.TraceConstant;
import com.sky.constant.RocketMqTopicConstant;
import com.sky.mq.message.FlashSaleOrderCreateMessage;
import com.sky.service.FlashSaleAsyncPersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "sky.rocketmq", name = "flash-sale-async-enabled", havingValue = "true")
@RocketMQMessageListener(
        // RocketMQ Spring 组件看到它标了 @RocketMQMessageListener,就给它创建一个消息监听容器
        // 这个容器一直盯着 FLASH_SALE_ORDER_CREATE_TOPIC,
        // 一旦这个 topic 来了新消息
        // 容器就自动调用： onMessage(message)
        topic = RocketMqTopicConstant.FLASH_SALE_ORDER_CREATE_TOPIC,
        consumerGroup = "peaksend-flash-sale-order-create-consumer",
        consumeMode = ConsumeMode.CONCURRENTLY
)
@RequiredArgsConstructor
@Slf4j
public class FlashSaleOrderCreateConsumer implements RocketMQListener<FlashSaleOrderCreateMessage> {

    private final FlashSaleAsyncPersistenceService flashSaleAsyncPersistenceService;

    @Override
    public void onMessage(FlashSaleOrderCreateMessage message) {
            try {
                if (message.getTraceId() != null && !message.getTraceId().isEmpty()) {
                    MDC.put(TraceConstant.TRACE_ID, message.getTraceId());
                }
                log.info("收到限量套餐异步落库消息：traceId={}, activityId={}, userId={}, orderNo={}",
                        message.getTraceId(), message.getActivityId(), message.getUserId(), message.getOrderNo());
                flashSaleAsyncPersistenceService.persistOrder(message);
            } finally {
                MDC.remove(TraceConstant.TRACE_ID);
            }
    }

}
