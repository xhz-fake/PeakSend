package com.sky.mq.consumer;

import com.sky.constant.RocketMqTopicConstant;
import com.sky.mq.message.OrderDelayCloseMessage;
import com.sky.service.OrderDelayCloseHandleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "sky.rocketmq", name = "order-delay-enabled", havingValue = "true")
@RocketMQMessageListener(
        topic = RocketMqTopicConstant.ORDER_DELAY_CLOSE_TOPIC,
        consumerGroup = "peaksend-order-delay-close-consumer",
        consumeMode = ConsumeMode.CONCURRENTLY
)
@RequiredArgsConstructor
@Slf4j
public class OrderDelayCloseConsumer implements RocketMQListener<OrderDelayCloseMessage> {

    private final OrderDelayCloseHandleService orderDelayCloseHandleService;

    @Override
    public void onMessage(OrderDelayCloseMessage message) {
        log.info("收到订单延迟关单消息：orderId={}, orderNumber={}",
                message.getOrderId(), message.getOrderNumber());
        orderDelayCloseHandleService.closeIfPending(message);
    }
}
