package com.sky.product.mapper;

import com.sky.vo.DishVO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DishMapper {

    DishVO getById(Long id);
}
