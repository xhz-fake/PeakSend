package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.annotation.AutoFill;
import com.sky.dto.CategoryPageQueryDTO;
import com.sky.entity.Category;
import com.sky.enumeration.OperationType;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CategoryMapper {

    /**
     * 插入分类数据
     *
     * @param category
     */
    @AutoFill(OperationType.INSERT)
    void insert(Category category);

    /**
     * 分类分页查询
     *
     * @param categoryPageQueryDTO
     * @return
     */
    Page<Category> pageQuery(CategoryPageQueryDTO categoryPageQueryDTO);

    /**
     * 动态更新分类信息
     *
     * @param category
     */
    @AutoFill(OperationType.UPDATE)
    void update(Category category);

    /**
     * 根据id删除分类
     *
     * @param id
     */
    void deleteById(Long id);

    /**
     * 根据条件查询分类
     *
     * @param category
     * @return
     */
    java.util.List<Category> list(Category category);
}
