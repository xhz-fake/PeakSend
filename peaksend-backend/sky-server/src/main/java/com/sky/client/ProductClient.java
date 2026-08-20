package com.sky.client;

import com.sky.vo.DishVO;
import com.sky.vo.SetmealVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "product-service")
// 不是写死 URL 而是按服务名 product-service 去找实例
public interface ProductClient {
    //表面上定义了一个 Java 接口
    //实际上告诉 Spring：
    //目标服务名叫 product-service
    //调这个方法时，请发 HTTP GET 到 /rpc/products/dishes/{id}

    @GetMapping("/rpc/products/dishes/{id}")
    DishVO getDishById(@PathVariable("id") Long id);

    @GetMapping("/rpc/products/setmeals/{id}")
    SetmealVO getSetmealById(@PathVariable("id") Long id);
}
