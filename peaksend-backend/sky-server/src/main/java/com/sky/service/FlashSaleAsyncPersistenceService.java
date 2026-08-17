package com.sky.service;

import com.sky.entity.FlashSaleSetmealActivity;
import com.sky.entity.FlashSaleSetmealOrder;
import com.sky.mapper.FlashSaleSetmealActivityMapper;
import com.sky.mapper.FlashSaleSetmealOrderMapper;
import com.sky.mq.message.FlashSaleOrderCreateMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class FlashSaleAsyncPersistenceService {

    private static final Integer FLASH_SALE_ORDER_SUCCESS = 1;

    private final FlashSaleSetmealActivityMapper flashSaleSetmealActivityMapper;
    private final FlashSaleSetmealOrderMapper flashSaleSetmealOrderMapper;

    //Day16 第一条链的完整代码路径：
    //1. 用户请求进来，执行 seize(activityId)
    //2. Lua 在 Redis 里原子抢资格
    //3. 抢到后不再同步写 MySQL
    //4. 改成 publisher.send(...) 发 MQ
    //5. Consumer.onMessage(...) 收消息
    //6. FlashSaleAsyncPersistenceService.persistOrder(...)
    //   - 查幂等
    //   - 扣 MySQL 活动库存
    //   - 插抢购订单
    //7. 日志打印“异步落库消费成功”

    @Transactional
    public void persistOrder(FlashSaleOrderCreateMessage message) {// 这是 Day16 第一条链最核心的代码。
        //第一步，先查幂等：同一个操作，执行 1 次和执行多次，最终结果应该一致，这就叫幂等。
        FlashSaleSetmealOrder existingOrder = flashSaleSetmealOrderMapper.getByActivityIdAndUserId(
                message.getActivityId(), message.getUserId());
        if (existingOrder != null) {
            log.info("限量套餐抢购消息已处理过，直接跳过：activityId={}, userId={}",
                    message.getActivityId(), message.getUserId());
            return;
        }
        //- 先查数据库里，这个用户对这个活动是不是已经有订单了,也就是查：同一个用户，对同一个活动，只允许存在一条成功抢购记录
        //- 如果已经有了，说明这条消息之前处理过
        //- 这次直接跳过，不再重复执行后面的扣库存、插订单

        //第二步，查活动是否还存在：
        FlashSaleSetmealActivity activity = flashSaleSetmealActivityMapper.getById(message.getActivityId());
        if (activity == null) {
            throw new IllegalStateException("限量套餐活动不存在，无法异步落库");
        }

        // 第三步，扣 MySQL 活动库存：
        int affected = flashSaleSetmealActivityMapper.decreaseStock(
                message.getActivityId(), LocalDateTime.now(), message.getUserId());
        if (affected <= 0) {
            throw new IllegalStateException("限量套餐库存落库失败，等待 RocketMQ 重试");
        }

        // 第四步，插抢购订单：
        flashSaleSetmealOrderMapper.insert(FlashSaleSetmealOrder.builder()
                .activityId(message.getActivityId())
                .userId(message.getUserId())
                .setmealId(activity.getSetmealId())
                .orderNo(message.getOrderNo())
                .activityName(activity.getActivityName())
                .setmealName(activity.getSetmealName())
                .setmealPrice(activity.getSetmealPrice())
                .setmealImage(activity.getSetmealImage())
                .status(FLASH_SALE_ORDER_SUCCESS)
                .createTime(message.getReservedTime() == null ? LocalDateTime.now() : message.getReservedTime())
                .build());

        // 第五步，打成功日志。
        log.info("限量套餐异步落库消费成功：activityId={}, userId={}, orderNo={}",
                message.getActivityId(), message.getUserId(), message.getOrderNo());
    }
}
