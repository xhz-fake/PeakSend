package com.sky.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SetmealDishMapper {

    /**
     * 统计指定菜品是否被套餐关联
     *
     * @param dishIds
     * @return
     */
    Integer countByDishIds(@Param("dishIds") List<Long> dishIds);
}
