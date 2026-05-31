package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.DishFlavor;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.mapper.DishFlavorMapper;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.result.PageResult;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service// 告诉 Spring：这是业务层类，请把它注册到容器里
public class DishServiceImpl implements DishService {
// 所以 DishServiceImpl 并不直接写 SQL，
// 它像一个调度员：
//- 该查主表时调谁
//- 该查子表时调谁
//- 该校验套餐关联时调谁

    @Autowired
    private DishMapper dishMapper;// 管菜品主表

    @Autowired
    private DishFlavorMapper dishFlavorMapper;// 管口味子表

    @Autowired
    private SetmealDishMapper setmealDishMapper;// 管“菜品是否被套餐引用”

    /**
     * 新增菜品，同时保存对应口味数据
     *
     * @param dishDTO ----> 前端传给后端的“整包数据”里面不仅有菜品本身，还有口味列表 flavors
     */
    @Override
    @Transactional// 确保事务的原子性，要么全部成功要么全部回滚
    public void saveWithFlavor(DishDTO dishDTO) {
        Dish dish = new Dish();// Dish对应数据库里的 dish 主表
        BeanUtils.copyProperties(dishDTO, dish);// 先把 DishDTO 变成 Dish
        //为什么不直接拿 DishDTO 去插表？
        //
        //因为：
        //- DishDTO 更像“请求包”
        //- Dish 更像“数据库主表对象”

        if (dish.getStatus() == null) {
            dish.setStatus(StatusConstant.ENABLE);//- 如果前端没传状态就默认给它设成起售
        }

        dishMapper.insert(dish);// 先插主表、
        //因为口味表 dish_flavor 里有一个字段：dish_id，而这个 dish_id 必须等于主表 dish 的主键 id。
        //所以流程必须是：
        //1. 先插 dish
        //2. 数据库生成主键 id
        //3. Java 对象拿到这个 id
        //4. 再让每条口味都挂到这个 id 上

        Long dishId = dish.getId();// 能拿到值，是因为 MyBatis 已经帮你把主键回填了。
        List<DishFlavor> flavors = dishDTO.getFlavors();// 对应数据库里的 dish_flavor 子表
        if (flavors == null || flavors.isEmpty()) {
            return;
        }

        flavors.forEach(flavor -> flavor.setDishId(dishId));// 给每条口味补 dishId
        dishFlavorMapper.insertBatch(flavors);// 批量插入口味
    }

    /**
     * 菜品分页查询
     *
     * @param dishPageQueryDTO
     * @return
     */
    @Override
    public PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO) {
        PageHelper.startPage(dishPageQueryDTO.getPage(), dishPageQueryDTO.getPageSize());

        Page<DishVO> page = dishMapper.pageQuery(dishPageQueryDTO);

        long total = page.getTotal();
        List<DishVO> records = page.getResult();

        return new PageResult(total, records);
    }

    /**
     * 批量删除菜品
     *
     * @param ids
     */
    @Override
    @Transactional
    public void deleteBatch(List<Long> ids) {
        List<Dish> dishes = dishMapper.getByIds(ids);
        for (Dish dish : dishes) {
            if (StatusConstant.ENABLE.equals(dish.getStatus())) {// 判断这道菜是不是起售中
                throw new DeletionNotAllowedException(MessageConstant.DISH_ON_SALE);
            }
        }

        Integer count = setmealDishMapper.countByDishIds(ids);
        if (count != null && count > 0) {// 判断这道菜有没有被套餐关联
            throw new DeletionNotAllowedException(MessageConstant.DISH_BE_RELATED_BY_SETMEAL);
        }

        dishMapper.deleteByIds(ids);
        dishFlavorMapper.deleteByDishIds(ids);
    }

    /**
     * 菜品起售停售
     *
     * @param status
     * @param id
     */
    @Override
    public void startOrStop(Integer status, Long id) {
        Dish dish = Dish.builder()
                .id(id)
                .status(status)
                .build();
        //它不是先查整条菜品再慢慢改，而是：
        //- 只构造一个最小 Dish 对象
        //- 只带 id 和 status
        //- 然后直接调动态更新

        dishMapper.update(dish);
    }

    /**
     * 根据id查询菜品和口味
     * @param id
     * @return
     */
    @Override
    public DishVO getByIdWithFlavor(Long id) {
        DishVO dishVO = dishMapper.getById(id);// 先查主体信息 dishVO
        List<DishFlavor> flavors = dishFlavorMapper.getByDishId(id);// 再查口味列表 flavors
        dishVO.setFlavors(flavors);
        return dishVO;
    }

    /**
     * 修改菜品和对应口味
     * @param dishDTO
     */
    @Override
    @Transactional
    public void updateWithFlavor(DishDTO dishDTO) {
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO, dish);
        dishMapper.update(dish);

        Long dishId = dishDTO.getId();
        dishFlavorMapper.deleteByDishIds(Collections.singletonList(dishId));

        List<DishFlavor> flavors = dishDTO.getFlavors();
        if (flavors == null || flavors.isEmpty()) {
            return;
        }

        flavors.forEach(flavor -> flavor.setDishId(dishId));
        dishFlavorMapper.insertBatch(flavors);
        //这个策略非常经典：
        //
        //1. 先更新主表
        //2. 再删掉这道菜原来的所有口味
        //3. 再把前端这次传来的新口味全部重新插进去
        //为什么不一个个比较“哪些口味变了、哪些删了、哪些新增了”？
        //因为那样虽然更精细，但对当前场景来说会复杂很多。
    }

    /**
     * 条件查询菜品并携带口味
     *
     * @param dish
     * @return
     */
    @Override
    public List<DishVO> listWithFlavor(Dish dish) {
        List<Dish> dishes = dishMapper.list(dish);
        return dishes.stream().map(item -> {
            DishVO dishVO = new DishVO();
            BeanUtils.copyProperties(item, dishVO);
            dishVO.setFlavors(dishFlavorMapper.getByDishId(item.getId()));
            return dishVO;
        }).collect(java.util.stream.Collectors.toList());
    }
}
