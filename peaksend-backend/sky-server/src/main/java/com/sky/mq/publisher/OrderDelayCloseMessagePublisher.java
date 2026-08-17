package com.sky.mq.publisher;

import com.sky.entity.Orders;

public interface OrderDelayCloseMessagePublisher {

    void send(Orders orders);
}
