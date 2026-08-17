package com.sky.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Day16 RocketMQ 业务开关配置。
 */
@Component
@ConfigurationProperties(prefix = "sky.rocketmq")
@Data
public class RocketMqBizProperties {

    /**
     * 是否启用 RocketMQ 能力。
     */
    private boolean enabled = false;

    /**
     * 是否启用订单延迟关单。
     */
    private boolean orderDelayEnabled = false;

    /**
     * 是否启用限量套餐异步落库。
     */
    private boolean flashSaleAsyncEnabled = false;

    /**
     * RocketMQ 延迟级别，默认 14 约 10 分钟。
     */
    private int orderDelayLevel = 14;
}
