package com.sky.product.service.impl;

import com.sky.product.mapper.DishMapper;
import com.sky.product.mapper.SetmealMapper;
import com.sky.product.service.ProductQueryService;
import com.sky.vo.DishVO;
import com.sky.vo.SetmealVO;
import org.springframework.stereotype.Service;

@Service
public class ProductQueryServiceImpl implements ProductQueryService {

    private final DishMapper dishMapper;
    private final SetmealMapper setmealMapper;

    public ProductQueryServiceImpl(DishMapper dishMapper, SetmealMapper setmealMapper) {
        this.dishMapper = dishMapper;
        this.setmealMapper = setmealMapper;
    }

    @Override
    public DishVO getDishById(Long id) {
        return dishMapper.getById(id);
    }

    @Override
    public SetmealVO getSetmealById(Long id) {
        return setmealMapper.getById(id);
    }
}
