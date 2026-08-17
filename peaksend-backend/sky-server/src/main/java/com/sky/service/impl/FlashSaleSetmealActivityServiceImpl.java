package com.sky.service.impl;

import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.context.BaseContext;
import com.sky.dto.FlashSaleSetmealActivityDTO;
import com.sky.entity.FlashSaleSetmealActivity;
import com.sky.entity.FlashSaleSetmealOrder;
import com.sky.exception.FlashSaleBusinessException;
import com.sky.mapper.FlashSaleSetmealActivityMapper;
import com.sky.mapper.FlashSaleSetmealOrderMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.mq.publisher.FlashSaleOrderCreateMessagePublisher;
import com.sky.service.FlashSaleSetmealActivityService;
import com.sky.vo.FlashSaleSetmealSeizeVO;
import com.sky.vo.SetmealVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class FlashSaleSetmealActivityServiceImpl implements FlashSaleSetmealActivityService {

    private static final String FLASH_SALE_ACTIVITY_KEY_PREFIX = "flashsale:activity:";
    private static final String FLASH_SALE_ACTIVITY_USERS_KEY_SUFFIX = ":users";
    private static final Integer FLASH_SALE_ORDER_SUCCESS = 1;

    private static final Long LUA_SUCCESS = 1L;//抢购成功
    private static final Long LUA_ACTIVITY_NOT_FOUND = -1L;// 活动不存在
    private static final Long LUA_ACTIVITY_DISABLED = -2L;// 活动没启用
    private static final Long LUA_ACTIVITY_NOT_STARTED = -3L;// 活动还没开始
    private static final Long LUA_ACTIVITY_ENDED = -4L;// 活动已结束
    private static final Long LUA_DUPLICATE_ORDER = -5L;// 重复抢购
    private static final Long LUA_STOCK_NOT_ENOUGH = -6L;// 库存不足
    //Lua 脚本执行完以后，不会直接给你返回一句中文提示，而是先返回一个“状态码”；Java 再根据这个状态码决定抛什么业务异常、给前端什么提示。

    private static final DefaultRedisScript<Long> FLASH_SALE_SEIZE_SCRIPT = createSeizeScript();
    //把“抢购脚本”提前加载成一个可执行对象，后面用户每次点击“立即抢购”时，Java 就会拿这个脚本对象直接去 Redis 执行。

    @Autowired
    private FlashSaleSetmealActivityMapper flashSaleSetmealActivityMapper;
    //- 管活动表
    //- 负责查活动、插活动、改状态、扣库存、删活动

    @Autowired
    private FlashSaleSetmealOrderMapper flashSaleSetmealOrderMapper;
    //- 管抢购记录表
    //- 负责查有没有抢购记录、插入抢购订单、查用户抢购历史

    @Autowired
    private SetmealMapper setmealMapper;
    //- 管套餐基础信息
    //- 创建活动时要先确认套餐存不存在，还要把套餐名、价格、图片快照带进活动里

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    //- 管 Redis
    //- 负责执行 Lua、预热活动缓存、维护已抢用户集合、必要时回补 Redis

    @Autowired
    private FlashSaleOrderCreateMessagePublisher flashSaleOrderCreateMessagePublisher;

    @Override
    @Transactional
    public void create(FlashSaleSetmealActivityDTO activityDTO) {
        validateActivityDTO(activityDTO);
        //管理端点“创建活动”后，不是直接插库，而是先过一轮业务规则校验。

        SetmealVO setmeal = setmealMapper.getById(activityDTO.getSetmealId());
        // 它说明“限量套餐活动”不是一个空壳活动，它必须挂在一个真实套餐上。如果套餐都不存在，那这个活动就没有业务意义，所以直接抛异常。
        if (setmeal == null) {
            throw new FlashSaleBusinessException(MessageConstant.FLASH_SALE_SETMEAL_NOT_FOUND);
        }

        LocalDateTime now = LocalDateTime.now();
        Long currentId = BaseContext.getCurrentId();
        FlashSaleSetmealActivity activity = FlashSaleSetmealActivity.builder()// 把“页面传来的参数” + “数据库查出来的套餐信息” + “系统自动生成的公共字段” 合成一条完整活动记录
                .setmealId(setmeal.getId())
                .activityName(buildActivityName(activityDTO, setmeal))
                .setmealName(setmeal.getName())
                .setmealPrice(setmeal.getPrice())
                .setmealImage(setmeal.getImage())
                .stock(activityDTO.getStock())
                .status(activityDTO.getStatus() == null ? StatusConstant.DISABLE : activityDTO.getStatus())
                .startTime(activityDTO.getStartTime())
                .endTime(activityDTO.getEndTime())
                .createTime(now)
                .updateTime(now)
                .createUser(currentId)
                .updateUser(currentId)
                .build();

        flashSaleSetmealActivityMapper.insert(activity);
        trySyncActivityCache(activity);
        //这一步是 Day15 的关键设计, 在插库后立刻同步 Redis， 活动一创建好，就立刻把热点字段预热进 Redis
    }

    @Override
    public List<FlashSaleSetmealActivity> listAll() {
        return flashSaleSetmealActivityMapper.listAll();
    }

    @Override
    public List<FlashSaleSetmealActivity> listAvailable() {
        return flashSaleSetmealActivityMapper.listAvailable(LocalDateTime.now());
    }

    @Override
    @Transactional
    public void updateStatus(Integer status, Long id) {
        if (status == null || (!StatusConstant.ENABLE.equals(status) && !StatusConstant.DISABLE.equals(status))) {
            throw new FlashSaleBusinessException(MessageConstant.FLASH_SALE_ACTIVITY_STATUS_INVALID);
        }

        FlashSaleSetmealActivity activity = flashSaleSetmealActivityMapper.getById(id);
        if (activity == null) {
            throw new FlashSaleBusinessException(MessageConstant.FLASH_SALE_ACTIVITY_NOT_FOUND);
        }

        flashSaleSetmealActivityMapper.updateStatus(id, status, LocalDateTime.now(), BaseContext.getCurrentId());
        trySyncActivityCache(getActivityOrThrow(id));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        FlashSaleSetmealActivity activity = flashSaleSetmealActivityMapper.getById(id);
        if (activity == null) {
            throw new FlashSaleBusinessException(MessageConstant.FLASH_SALE_ACTIVITY_NOT_FOUND);
        }

        Integer orderCount = flashSaleSetmealOrderMapper.countByActivityId(id);
        if (orderCount != null && orderCount > 0) {
            throw new FlashSaleBusinessException(MessageConstant.FLASH_SALE_ACTIVITY_HAS_ORDER_RECORDS);
        }

        flashSaleSetmealActivityMapper.deleteById(id);
        removeActivityCache(id);
    }

    @Override
    @Transactional
    public FlashSaleSetmealSeizeVO seize(Long activityId) {// Day15的核心所在,
        //记成一句话：- 用户点击抢购后，系统先在 Redis 里用 Lua 原子抢资格，抢到后再落 MySQL；如果落库失败，就回补 Redis，避免库存状态脏掉。
        //  分工是：
        //- Lua 负责抢资格
        //- Java 负责落数据库
        //- Java 失败时再回补 Redis
        Long userId = BaseContext.getCurrentId();
        //第1步：拿当前用户,“一人一单”后面就是拿这个 userId 去判断。

        Long scriptResult;
        try {
            //第2步：先打 Redis + Lua，不先打数据库
            ensureActivityCacheLoaded(activityId);// 先确保 Redis 里有这场活动
            scriptResult = stringRedisTemplate.execute(// 然后直接执行 Lua 脚本
                    FLASH_SALE_SEIZE_SCRIPT,
                    Arrays.asList(buildActivityKey(activityId), buildActivityUsersKey(activityId)),
                    String.valueOf(userId),
                    String.valueOf(toEpochSecond(LocalDateTime.now()))
            );
            //Lua 这次一次性做掉了：
            //- 活动是否存在
            //- 是否启用
            //- 是否开始
            //- 是否结束
            //- 库存够不够
            //- 用户是否已经抢过
            //并且成功时还会顺手做两件事：- Redis 库存减 1, 用户写入已抢购集合
        } catch (DataAccessException ex) {
            log.warn("限量套餐抢购依赖的 Redis 不可用，activityId={}, userId={}", activityId, userId, ex);
            throw new FlashSaleBusinessException(MessageConstant.FLASH_SALE_REDIS_UNAVAILABLE);
        }
        //第3步：把 Lua 返回码翻译成业务结果
        validateScriptResult(scriptResult);

        FlashSaleSetmealActivity activity = getActivityOrThrow(activityId);
        String orderNo = buildOrderNo(activityId, userId);

        if (flashSaleOrderCreateMessagePublisher.supportAsyncPersistence()) {
            try {
                // Day16 真正改动点，就在这里：
                //第4步：Redis 抢到资格后，先发 MQ 消息，消息发成功就直接返回前端，MySQL 落库交给消费者
                flashSaleOrderCreateMessagePublisher.send(activityId, userId, orderNo, LocalDateTime.now());
                return FlashSaleSetmealSeizeVO.builder()
                        .activityId(activityId)
                        .setmealId(activity.getSetmealId())
                        .orderNo(orderNo)
                        .build();
            } catch (RuntimeException ex) {// 发送 MQ 失败
                compensateReservation(activityId, userId);
                // 如果 MQ 发送失败要把 Redis 撤回去
                log.warn("限量套餐抢购消息投递失败，已回补 Redis 预扣库存。activityId={}, userId={}", activityId, userId, ex);
                throw new FlashSaleBusinessException("限量套餐抢购消息投递失败");
            }
        }

        try {
            //这里是第二段主链：
            //1. 先更新活动表库存
            return persistSeizeResult(activityId, userId, activity, orderNo);
        } catch (FlashSaleBusinessException ex) {
            //第5步：落库失败要回补 Redis
            //
            //因为前面 Lua 已经把：
            //- Redis 库存扣了
            //- 用户集合写了
            //
            //如果后面 MySQL 更新库存或插订单失败，而你什么都不做，就会出现：
            //- Redis 觉得这人抢成功了
            //- 数据库却没有成功订单
            compensateReservation(activityId, userId);
            throw ex;
        } catch (DataIntegrityViolationException ex) {
            compensateReservation(activityId, userId);
            throw new FlashSaleBusinessException(MessageConstant.FLASH_SALE_DUPLICATE_ORDER);
        } catch (RuntimeException ex) {
            compensateReservation(activityId, userId);
            throw ex;
        }
    }

    @Override
    public List<FlashSaleSetmealOrder> listCurrentUserOrders() {
        return flashSaleSetmealOrderMapper.listByUserId(BaseContext.getCurrentId());
    }

    private void validateActivityDTO(FlashSaleSetmealActivityDTO activityDTO) {
        if (activityDTO == null || activityDTO.getSetmealId() == null) {
            throw new FlashSaleBusinessException(MessageConstant.FLASH_SALE_SETMEAL_NOT_FOUND);
        }
        if (activityDTO.getStock() == null || activityDTO.getStock() <= 0) {
            throw new FlashSaleBusinessException(MessageConstant.FLASH_SALE_ACTIVITY_STOCK_INVALID);
        }
        if (activityDTO.getStartTime() == null
                || activityDTO.getEndTime() == null
                || !activityDTO.getEndTime().isAfter(activityDTO.getStartTime())) {
            throw new FlashSaleBusinessException(MessageConstant.FLASH_SALE_ACTIVITY_TIME_INVALID);
        }
        Integer status = activityDTO.getStatus();
        if (status != null && !StatusConstant.ENABLE.equals(status) && !StatusConstant.DISABLE.equals(status)) {
            throw new FlashSaleBusinessException(MessageConstant.FLASH_SALE_ACTIVITY_STATUS_INVALID);
        }
    }

    private String buildActivityName(FlashSaleSetmealActivityDTO activityDTO, SetmealVO setmeal) {
        if (StringUtils.hasText(activityDTO.getActivityName())) {
            return activityDTO.getActivityName().trim();
        }
        return setmeal.getName() + "限量活动";
    }

    private void ensureActivityCacheLoaded(Long activityId) {
        //这个是兜底逻辑，意思是：
        //- 如果 Redis 里这场活动的数据没了
        //- 就从数据库再加载一次，重新预热回 Redis
        String activityKey = buildActivityKey(activityId);
        Boolean exists = stringRedisTemplate.hasKey(activityKey);
        if (Boolean.TRUE.equals(exists)) {
            return;
        }

        FlashSaleSetmealActivity activity = flashSaleSetmealActivityMapper.getById(activityId);
        if (activity != null) {
            syncActivityCache(activity);
        }
    }

    private void syncActivityCache(FlashSaleSetmealActivity activity) {
        // 把 MySQL 里的活动状态，整形成 Redis 抢购专用数据。
        String activityKey = buildActivityKey(activity.getId());
        String activityUsersKey = buildActivityUsersKey(activity.getId());
        //先算出两个 key
        //- 活动 Hash： flashsale:activity:{id}
        //- 用户 Set： flashsale:activity:{id}:users

        stringRedisTemplate.delete(Arrays.asList(activityKey, activityUsersKey));
        // 先删旧缓存- 避免旧库存、旧状态、旧用户集合残留

        Map<String, String> activityCache = new HashMap<>();
        activityCache.put("status", String.valueOf(activity.getStatus()));
        activityCache.put("stock", String.valueOf(activity.getStock()));
        activityCache.put("startTime", String.valueOf(toEpochSecond(activity.getStartTime())));
        activityCache.put("endTime", String.valueOf(toEpochSecond(activity.getEndTime())));
        activityCache.put("setmealId", String.valueOf(activity.getSetmealId()));
        stringRedisTemplate.opsForHash().putAll(activityKey, activityCache);
        //把活动核心字段写进 Hash
        //- status
        //- stock
        //- startTime
        //- endTime
        //- setmealId

        List<Long> userIds = flashSaleSetmealOrderMapper.listUserIdsByActivityId(activity.getId());
        if (userIds != null && !userIds.isEmpty()) {
            String[] userIdArray = userIds.stream().map(String::valueOf).toArray(String[]::new);
            stringRedisTemplate.opsForSet().add(activityUsersKey, userIdArray);
        }
        //把已经抢到过的用户写进 Set- 这样“一人一单”就能直接在 Redis 里判断

        Duration ttl = buildActivityCacheTtl(activity.getEndTime());
        stringRedisTemplate.expire(activityKey, ttl);
        stringRedisTemplate.expire(activityUsersKey, ttl);
    }

    private void trySyncActivityCache(FlashSaleSetmealActivity activity) {
        try {
            syncActivityCache(activity);
        } catch (DataAccessException ex) {
            log.warn("限量套餐活动已落库，但 Redis 预热失败。activityId={}", activity.getId(), ex);
        }
        //- MySQL 落库是主链
        //- Redis 预热失败不能把整个创建活动直接打死，Redis 预热失败就记日志，后面再补救
    }

    private void removeActivityCache(Long activityId) {
        try {
            stringRedisTemplate.delete(Arrays.asList(buildActivityKey(activityId), buildActivityUsersKey(activityId)));
        } catch (DataAccessException ex) {
            log.warn("限量套餐活动已删除，但 Redis 缓存清理失败。activityId={}", activityId, ex);
        }
    }

    private Duration buildActivityCacheTtl(LocalDateTime endTime) {
        Duration ttl = Duration.between(LocalDateTime.now(), endTime.plusDays(1));
        if (ttl.isNegative() || ttl.isZero()) {
            return Duration.ofDays(1);
        }
        return ttl;
    }

    private void validateScriptResult(Long scriptResult) {
        if (scriptResult == null) {
            throw new FlashSaleBusinessException("限量套餐抢购执行失败");
        }
        if (LUA_SUCCESS.equals(scriptResult)) {
            return;
        }
        if (LUA_ACTIVITY_NOT_FOUND.equals(scriptResult)) {
            throw new FlashSaleBusinessException(MessageConstant.FLASH_SALE_ACTIVITY_NOT_FOUND);
        }
        if (LUA_ACTIVITY_DISABLED.equals(scriptResult)) {
            throw new FlashSaleBusinessException(MessageConstant.FLASH_SALE_ACTIVITY_DISABLED);
        }
        if (LUA_ACTIVITY_NOT_STARTED.equals(scriptResult)) {
            throw new FlashSaleBusinessException(MessageConstant.FLASH_SALE_ACTIVITY_NOT_STARTED);
        }
        if (LUA_ACTIVITY_ENDED.equals(scriptResult)) {
            throw new FlashSaleBusinessException(MessageConstant.FLASH_SALE_ACTIVITY_ENDED);
        }
        if (LUA_DUPLICATE_ORDER.equals(scriptResult)) {
            throw new FlashSaleBusinessException(MessageConstant.FLASH_SALE_DUPLICATE_ORDER);
        }
        if (LUA_STOCK_NOT_ENOUGH.equals(scriptResult)) {
            throw new FlashSaleBusinessException(MessageConstant.FLASH_SALE_STOCK_NOT_ENOUGH);
        }
        throw new FlashSaleBusinessException("限量套餐抢购执行失败");
        //Lua 负责返回状态码，Java 负责把状态码翻译成业务异常。
    }

    private void compensateReservation(Long activityId, Long userId) {
        try {
            // 如果 Redis 先扣成功了，但 MySQL 落库失败，就把 Redis 加回去。
            String activityKey = buildActivityKey(activityId);
            String activityUsersKey = buildActivityUsersKey(activityId);
            stringRedisTemplate.opsForHash().increment(activityKey, "stock", 1);// stock + 1
            stringRedisTemplate.opsForSet().remove(activityUsersKey, String.valueOf(userId)); // 从已抢用户集合里删掉当前用户
            log.warn("限量套餐抢购持久化失败，已回补 Redis 预扣库存。activityId={}, userId={}", activityId, userId);
        } catch (DataAccessException ex) {
            log.warn("限量套餐抢购回补 Redis 失败。activityId={}, userId={}", activityId, userId, ex);
        }
    }

    private FlashSaleSetmealActivity getActivityOrThrow(Long activityId) {
        FlashSaleSetmealActivity activity = flashSaleSetmealActivityMapper.getById(activityId);
        if (activity == null) {
            throw new FlashSaleBusinessException(MessageConstant.FLASH_SALE_ACTIVITY_NOT_FOUND);
        }
        return activity;
    }

    private String buildActivityKey(Long activityId) {
        return FLASH_SALE_ACTIVITY_KEY_PREFIX + activityId;
    }

    private String buildActivityUsersKey(Long activityId) {
        return buildActivityKey(activityId) + FLASH_SALE_ACTIVITY_USERS_KEY_SUFFIX;
    }

    private long toEpochSecond(LocalDateTime time) {
        return time.toEpochSecond(ZoneOffset.ofHours(8));
    }

    private String buildOrderNo(Long activityId, Long userId) {
        return "FS" + activityId + userId + System.currentTimeMillis();
    }

    private FlashSaleSetmealSeizeVO persistSeizeResult(Long activityId, Long userId,
                                                       FlashSaleSetmealActivity activity, String orderNo) {
        int affected = flashSaleSetmealActivityMapper.decreaseStock(activityId, LocalDateTime.now(), userId);
        if (affected <= 0) {
            throw new FlashSaleBusinessException(MessageConstant.FLASH_SALE_STOCK_NOT_ENOUGH);
        }

        flashSaleSetmealOrderMapper.insert(FlashSaleSetmealOrder.builder()
                .activityId(activityId)
                .userId(userId)
                .setmealId(activity.getSetmealId())
                .orderNo(orderNo)
                .activityName(activity.getActivityName())
                .setmealName(activity.getSetmealName())
                .setmealPrice(activity.getSetmealPrice())
                .setmealImage(activity.getSetmealImage())
                .status(FLASH_SALE_ORDER_SUCCESS)
                .createTime(LocalDateTime.now())
                .build());

        return FlashSaleSetmealSeizeVO.builder()
                .activityId(activityId)
                .setmealId(activity.getSetmealId())
                .orderNo(orderNo)
                .build();
    }

    private static DefaultRedisScript<Long> createSeizeScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/flash_sale_setmeal_seize.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
