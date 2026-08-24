package com.sky.mq.consumer;

import com.sky.constant.TraceConstant;
import com.sky.constant.RocketMqTopicConstant;
import com.sky.mq.message.OrderDelayCloseMessage;
import com.sky.service.OrderDelayCloseHandleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
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
            try {
                if (message.getTraceId() != null && !message.getTraceId().isEmpty()) {
                    MDC.put(TraceConstant.TRACE_ID, message.getTraceId());
                }
                log.info("收到订单延迟关单消息：traceId={}, orderId={}, orderNumber={}",
                        message.getTraceId(), message.getOrderId(), message.getOrderNumber());
                orderDelayCloseHandleService.closeIfPending(message);
            } finally {
                MDC.remove(TraceConstant.TRACE_ID);
            }
    }
}
