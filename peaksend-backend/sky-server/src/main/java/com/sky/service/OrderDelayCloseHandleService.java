package com.sky.service;

import com.sky.entity.Orders;
import com.sky.mapper.OrdersMapper;
import com.sky.mq.message.OrderDelayCloseMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderDelayCloseHandleService {

    private final OrdersMapper ordersMapper;

    @Transactional
    public void closeIfPending(OrderDelayCloseMessage message) {
        Orders orders = ordersMapper.getByNumber(message.getOrderNumber());
        if (orders == null) {
            log.warn("延迟关单消息对应订单不存在，跳过：orderNumber={}", message.getOrderNumber());
            return;
        }
        if (!Orders.PENDING_PAYMENT.equals(orders.getStatus()) || !Orders.UN_PAID.equals(orders.getPayStatus())) {
            log.info("订单已支付或已流转，跳过延迟关单：orderId={}, status={}, payStatus={}",
                    orders.getId(), orders.getStatus(), orders.getPayStatus());
            return;
        }

        Orders updateOrder = new Orders();
        updateOrder.setId(orders.getId());
        updateOrder.setStatus(Orders.CANCELLED);
        updateOrder.setPayStatus(Orders.UN_PAID);
        updateOrder.setCancelReason("支付超时，系统自动取消");
        updateOrder.setCancelTime(LocalDateTime.now());
        ordersMapper.update(updateOrder);
        log.info("延迟关单消费成功：orderId={}, orderNumber={}", orders.getId(), orders.getNumber());
    }
}
