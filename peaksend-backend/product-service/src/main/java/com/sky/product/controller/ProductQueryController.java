package com.sky.product.controller;

import com.sky.product.service.ProductQueryService;
import com.sky.vo.DishVO;
import com.sky.vo.SetmealVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rpc/products")
public class ProductQueryController {

    private final ProductQueryService productQueryService;

    public ProductQueryController(ProductQueryService productQueryService) {
        this.productQueryService = productQueryService;
    }

    @GetMapping("/dishes/{id}")
    public DishVO getDishById(@PathVariable Long id) {
        return productQueryService.getDishById(id);
    }

    @GetMapping("/setmeals/{id}")
    public SetmealVO getSetmealById(@PathVariable Long id) {
        return productQueryService.getSetmealById(id);
    }
}
