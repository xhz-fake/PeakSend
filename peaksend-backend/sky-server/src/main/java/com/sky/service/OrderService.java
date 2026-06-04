package com.sky.service;

import com.sky.dto.OrdersPaymentDTO;
import com.sky.result.PageResult;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;

public interface OrderService {

    /**
     * 用户提交订单
     *
     * @param ordersSubmitDTO
     * @return
     */
    OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO);

    /**
     * 用户取消订单
     *
     * @param id
     */
    void cancelOrder(Long id);

    /**
     * 查询订单详情
     *
     * @param id
     * @return
     */
    OrderVO details(Long id);

    /**
     * 用户端历史订单分页查询
     *
     * @param page 页码
     * @param pageSize 每页条数
     * @param status 订单状态，可为空
     * @return 分页结果
     */
    PageResult pageQuery4User(int page, int pageSize, Integer status);

    /**
     * 再来一单
     *
     * @param id 订单 id
     */
    void repetition(Long id);

    /**
     * 订单支付
     *
     * @param ordersPaymentDTO 支付参数
     * @return 支付调起参数
     * @throws Exception 调用微信支付异常
     */
    OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception;

    /**
     * 支付成功回调后的订单状态更新
     *
     * @param outTradeNo 商户订单号
     */
    void paySuccess(String outTradeNo);

    /**
     * 开发态模拟支付成功
     *
     * @param orderNumber 订单号
     */
    void mockPaySuccess(String orderNumber);
}
