package com.sky.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis 缓存过期时间配置。
 */
@Component// 把这个类交给 Spring 容器管理。让这个类变成 Spring 管理的 Bean，后面别的类才能注入它。
@ConfigurationProperties(prefix = "sky.cache")
//这就告诉 Spring：
// 你只需要盯住 sky.cache 下面的内容
// 然后把里面的每个配置项，找对应字段装进去

@Data//它会在编译时，自动帮这个类生成一堆常用方法，包括：getter，setter，toString()，equals()，hashCode()
public class CacheTtlProperties {

    /**
     * 未单独指定时的默认 TTL。
     */
    private Duration defaultTtl = Duration.ofMinutes(30);

    /**
     * 分类列表缓存 TTL。
     */
    private Duration categoryTtl = Duration.ofMinutes(20);

    /**
     * 菜品列表缓存 TTL。
     */
    private Duration dishTtl = Duration.ofMinutes(30);

    /**
     * 套餐列表缓存 TTL。
     */
    private Duration setmealTtl = Duration.ofMinutes(35);

    /**
     * 地址簿按 id 查询缓存 TTL。
     */
    private Duration addressBookByIdTtl = Duration.ofMinutes(2);

    /**
     * 报表统计结果缓存 TTL。
     */
    private Duration reportTtl = Duration.ofMinutes(5);

    //为什么不全都配成 30 分钟？
    //
    //因为如果所有热点缓存都在同一时刻过期，就容易出现：
    // 某一时间点大量请求同时回打数据库
    // 这会让数据库压力突然上来。所以第一版更稳的做法是：让不同缓存错峰过期。
    // 这也是：初级版抗雪崩意识
}
