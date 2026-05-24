package com.sky.mapper;

import com.sky.entity.SetmealDish;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SetmealDishMapper {

    /**
     * 批量保存套餐和菜品的关联关系
     *
     * @param setmealDishes
     */
    void insertBatch(@Param("setmealDishes") List<SetmealDish> setmealDishes);

    /**
     * 根据套餐id查询关联的菜品数据
     *
     * @param setmealId
     * @return
     */
    List<SetmealDish> getBySetmealId(Long setmealId);

    /**
     * 根据套餐id集合删除关联关系
     *
     * @param setmealIds
     */
    void deleteBySetmealIds(@Param("setmealIds") List<Long> setmealIds);

    /**
     * 统计指定菜品是否被套餐关联
     *
     * @param dishIds
     * @return
     */
    Integer countByDishIds(@Param("dishIds") List<Long> dishIds);
}
