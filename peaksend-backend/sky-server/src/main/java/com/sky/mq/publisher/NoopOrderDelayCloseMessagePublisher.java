package com.sky.mq.publisher;

import com.sky.entity.Orders;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "sky.rocketmq", name = "order-delay-enabled", havingValue = "false", matchIfMissing = true)
public class NoopOrderDelayCloseMessagePublisher implements OrderDelayCloseMessagePublisher {

    @Override
    public void send(Orders orders) {
        // RocketMQ 关闭时保持订单同步主链不变。
    }
}
