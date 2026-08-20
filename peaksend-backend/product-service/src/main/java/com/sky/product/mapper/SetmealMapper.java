package com.sky.product.mapper;

import com.sky.vo.SetmealVO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SetmealMapper {

    SetmealVO getById(Long id);
}
