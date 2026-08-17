package com.sky.mq.publisher;

import com.sky.constant.RocketMqTopicConstant;
import com.sky.entity.Orders;
import com.sky.mq.message.OrderDelayCloseMessage;
import com.sky.properties.RocketMqBizProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "sky.rocketmq", name = "order-delay-enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class RocketMqOrderDelayCloseMessagePublisher implements OrderDelayCloseMessagePublisher {

    private final RocketMQTemplate rocketMQTemplate;
    private final RocketMqBizProperties rocketMqBizProperties;

    @Override
    public void send(Orders orders) {
        OrderDelayCloseMessage message = OrderDelayCloseMessage.builder()
                .orderId(orders.getId())
                .userId(orders.getUserId())
                .orderNumber(orders.getNumber())
                .orderTime(orders.getOrderTime())
                .build();

        rocketMQTemplate.syncSend(
                RocketMqTopicConstant.ORDER_DELAY_CLOSE_TOPIC,
                MessageBuilder.withPayload(message).build(),
                3000,
                rocketMqBizProperties.getOrderDelayLevel()
        );
        log.info("发送订单延迟关单消息成功：orderId={}, orderNumber={}", orders.getId(), orders.getNumber());
    }
}
