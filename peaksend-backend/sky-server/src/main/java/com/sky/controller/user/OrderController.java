package com.sky.controller.user;

import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.OrderService;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDateTime;

/**
 * 用户端订单相关接口
 */
@RestController
@RequestMapping("/user/order")
@Slf4j
@Api(tags = "用户端订单相关接口")
public class OrderController {

    @Autowired// 注入对象
    private OrderService orderService;

    /**
     * 提交订单
     *
     * @param ordersSubmitDTO
     * @return
     */
    @PostMapping("/submit") // 也就是前端点击“去支付”后，真正打到后端的下单接口
    @ApiOperation("提交订单")
    public Result<OrderSubmitVO> submit(@RequestBody OrdersSubmitDTO ordersSubmitDTO) {
        log.info("提交订单：{}", ordersSubmitDTO);
        return Result.success(orderService.submitOrder(ordersSubmitDTO));
    }

    /**
     * 取消订单
     *
     * @param id
     * @return
     */
    @PutMapping("/cancel/{id}")
    @ApiOperation("取消订单")
    public Result cancel(@PathVariable Long id) {
        log.info("取消订单：{}", id);
        orderService.cancelOrder(id);
        return Result.success();
    }

    /**
     * 查询订单详情
     *
     * @param id
     * @return
     */
    @GetMapping("/orderDetail/{id}")
    @ApiOperation("查询订单详情")
    public Result<OrderVO> details(@PathVariable Long id) {
        log.info("查询订单详情：{}", id);
        return Result.success(orderService.details(id));
    }

    /**
     * 历史订单分页查询
     *
     * @param page 页码
     * @param pageSize 每页条数
     * @param status 订单状态
     * @return 分页结果
     */
    @GetMapping("/historyOrders")
    @ApiOperation("历史订单分页查询")
    public Result<PageResult> historyOrders(int page, int pageSize, Integer status) {
        log.info("历史订单分页查询：page={}, pageSize={}, status={}", page, pageSize, status);
        return Result.success(orderService.pageQuery4User(page, pageSize, status));
    }

    /**
     * 获取预估送达时间
     *
     * @param shopId 商铺 id
     * @param customerAddress 收货地址
     * @return 预估送达时间
     */
    @GetMapping("/getEstimatedDeliveryTime")
    @ApiOperation("获取预估送达时间")
    public Result<LocalDateTime> getEstimatedDeliveryTime(Long shopId, String customerAddress) {
        log.info("获取预估送达时间：shopId={}, customerAddress={}", shopId, customerAddress);
        return Result.success(LocalDateTime.now().plusHours(1));
    }

    /**
     * 再来一单
     *
     * @param id 订单 id
     * @return 处理结果
     */
    @PostMapping("/repetition/{id}")
    @ApiOperation("再来一单")
    public Result repetition(@PathVariable Long id) {
        log.info("再来一单：{}", id);
        orderService.repetition(id);
        return Result.success();
    }

    /**
     * 订单支付
     *
     * @param ordersPaymentDTO 支付参数
     * @return 支付调起参数
     * @throws Exception 调用微信支付异常
     */
    @PutMapping("/payment")//payment 只是“开始支付 / 调起支付”
    @ApiOperation("订单支付")
    public Result<OrderPaymentVO> payment(@RequestBody OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        log.info("订单支付：{}", ordersPaymentDTO);
        return Result.success(orderService.payment(ordersPaymentDTO));
    }

    /**
     * 开发态模拟支付成功
     *
     * @param orderNumber 订单号
     * @return 处理结果
     */
    @PutMapping("/mockPaySuccess/{orderNumber}") //mockPaySuccess 才是在模拟“支付成功后更新订单状态”
    @ApiOperation("开发态模拟支付成功")
    public Result mockPaySuccess(@PathVariable String orderNumber) {
        log.info("开发态模拟支付成功：{}", orderNumber);
        orderService.mockPaySuccess(orderNumber);
        return Result.success();
    }
}
