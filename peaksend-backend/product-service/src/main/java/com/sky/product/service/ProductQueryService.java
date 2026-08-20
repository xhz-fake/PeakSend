package com.sky.product.service;

import com.sky.vo.DishVO;
import com.sky.vo.SetmealVO;

public interface ProductQueryService {

    DishVO getDishById(Long id);

    SetmealVO getSetmealById(Long id);
}
