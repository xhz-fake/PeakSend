package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.SetmealDish;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.exception.SetmealEnableFailedException;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.service.SetmealService;
import com.sky.vo.DishItemVO;
import com.sky.vo.SetmealVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service
public class SetmealServiceImpl implements SetmealService {

    @Autowired
    private SetmealMapper setmealMapper;

    @Autowired
    private SetmealDishMapper setmealDishMapper;

    @Autowired
    private DishMapper dishMapper;

    /**
     * 新增套餐，同时保存套餐和菜品关联关系
     *
     * @param setmealDTO
     */
    @Override // 这个方法是在实现接口 SetmealService 里定义的方法
    @Transactional // 事物的原子性
    public void saveWithDish(SetmealDTO setmealDTO) {
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);// - 把 setmealDTO 里同名字段的值拷贝到 setmeal 里

        if (setmeal.getStatus() == null) {
            setmeal.setStatus(StatusConstant.DISABLE);
        }
        //- 如果前端这次没传状态
        //- 那后端就自己兜底，默认给它设成停售

        setmealMapper.insert(setmeal);// 先插套餐主表

        Long setmealId = setmeal.getId();// 拿到套餐 id
        // - MyBatis 插入时用了主键回填
        //- 数据库生成主键后，会把这个 id 自动回填到 Java 对象里
        //所以现在：
        //- setmeal 这个对象
        //- 已经不再是“纯内存里的空壳”
        //- 而是带着数据库生成主键的实体对象了

        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();// - 主表已经存好了，现在开始处理前端传来的“套餐包含哪些菜”
        if (setmealDishes == null || setmealDishes.isEmpty()) {
            return;
        }

        setmealDishes.forEach(setmealDish -> setmealDish.setSetmealId(setmealId));// 给每条关系补 setmealId
        setmealDishMapper.insertBatch(setmealDishes);//批量插入关系表
    }

    /**
     * 套餐分页查询
     *
     * @param setmealPageQueryDTO
     * @return
     */
    @Override
    public PageResult pageQuery(SetmealPageQueryDTO setmealPageQueryDTO) {
        PageHelper.startPage(setmealPageQueryDTO.getPage(), setmealPageQueryDTO.getPageSize()); // 就是：“接下来我要做分页查询了”，“第几页、每页几条，你先记住”
        // PageHelper 不是普通工具类，它会在后面 Mapper 查询执行时介入，自动帮你做两件事：
        //1. 先生成一条 count SQL，查总数
        //2. 再给原查询补上分页限制，比如 limit
        //- 它要先把“第几页、每页几条”这两个分页条件提前注册好
        //- 这样后面的 setmealMapper.pageQuery(...) 执行时，PageHelper 才能拦截这次查询，自动补上 count 和 limit

        Page<SetmealVO> page = setmealMapper.pageQuery(setmealPageQueryDTO);
        //因为分页查询返回的不只是普通列表，它还要额外带分页信息，比如：
        //- 总条数
        //- 当前页结果

        //所以 PageHelper 包装后的结果，不再只是 List 而是：Page<T>

        long total = page.getTotal();// 表示u总共有多少条符合条件的数据
        List<SetmealVO> records = page.getResult();// 表示当前这一页真正返回的数据列表

        return new PageResult(total, records);// 因为项目里前后端约定好的分页返回结构就是：total,records
        //- 之所以能直接传两个参数，是因为 PageResult 上有 @AllArgsConstructor
        //- 这个注解是 Lombok 提供的，它会在编译期自动帮我们生成“全参数构造器”
    }
    //pageQuery() 真正练到的是这 5 件事：
    //- SetmealPageQueryDTO 用来承接查询条件
    //- PageHelper.startPage() 用来启动分页
    //- Mapper.pageQuery() 执行真正查询
    //- SetmealVO 负责承接前端展示字段
    //- PageResult 负责统一分页返回结构

    /**
     * 套餐起售停售
     *
     * @param status
     * @param id
     */
    @Override
    public void startOrStop(Integer status, Long id) {
        if (StatusConstant.ENABLE.equals(status)) {// 起售：严格检验
            List<SetmealDish> setmealDishes = setmealDishMapper.getBySetmealId(id);// 首先要解决的问题：这个套餐到底关联了哪些菜？
            if (setmealDishes != null && !setmealDishes.isEmpty()) {// 虽然业务上一个套餐通常应该有关联菜品，但代码层面还是先防一下空值更稳。
                List<Long> dishIds = new java.util.ArrayList<>();
                for (SetmealDish setmealDish : setmealDishes) {
                    dishIds.add(setmealDish.getDishId());// 将关系对象列表转成: 菜品 id 列表
                }
                //顺序必须是：
                //1. 先从关系表里找出关联菜品

                List<Dish> dishes = dishMapper.getByIds(dishIds);
                //2. 再根据这些菜品 id 去菜品主表查状态,因为真正的菜品状态 status 在 dish 主表
                for (Dish dish : dishes) {// 真正的业务规则校验
                    if (StatusConstant.DISABLE.equals(dish.getStatus())) {//只要套餐里有任意一道菜是停售状态那就不允许启售整个套餐
                        throw new SetmealEnableFailedException(MessageConstant.SETMEAL_ENABLE_FAILED);// 明确拦截不合法的业务状态切换
                    }
                    //这也是一种很典型的企业级写法：
                    //- 业务不通过 -> 抛业务异常
                    //- 统一异常处理器 -> 转成规范响应

                }
            }
        }

        // 停售：直接允许
        Setmeal setmeal = new Setmeal();
        //- 不需要先把整条套餐完整查出来再改
        //- 只需要构造一个“最小对象”
        //- 里面只放：id, status
        //然后交给动态更新 SQL。
        setmeal.setId(id);
        setmeal.setStatus(status);
        setmealMapper.update(setmeal);
        // SetmealMapper.xml 里的 update 是动态更新：
        //- 哪个字段不为空
        //- 就更新哪个字段
        //所以这里传一个只带 id 和 status 的对象就够了。
    }

    /**
     * 批量删除套餐
     *
     * @param ids
     */
    @Override
    @Transactional // 事务保障了要么任务完成，要么干脆不做
    public void deleteBatch(List<Long> ids) {// 参数本身就是一个 id 的集合，表明我们支持批量操作
        List<Setmeal> setmeals = setmealMapper.getByIds(ids);// 先拿到这些套餐当前的状态
        for (Setmeal setmeal : setmeals) {
            if (StatusConstant.ENABLE.equals(setmeal.getStatus())) {// 先判断这写套餐能不能删（先进行Services层的业务判断，再去数据库进行操作）
                throw new DeletionNotAllowedException(MessageConstant.SETMEAL_ON_SALE);// 抛业务异常，然后全局异常处理器会统一把它转成前端能理解的Json结果
            }
            // 所以这里采取的是更稳的策略：
            //- 要么全部满足删除条件
            //- 要么整个删除请求失败
            // 否则容易产生歧义
        }

        setmealMapper.deleteByIds(ids);// 先删主表
        setmealDishMapper.deleteBySetmealIds(ids);// 再删关系表，这里也用到了事务的原子性
    }

    /**
     * 根据id查询套餐和关联菜品
     *
     * @param id
     * @return
     */
    @Override
    public SetmealVO getByIdWithDish(Long id) {
        SetmealVO setmealVO = setmealMapper.getById(id);// 详情查询，根据当前id针对性的查询
        // 为什么这里查出来直接就是 SetmealVO: 因为这条 SQL 在 SetmealMapper.xml 里已经把：
        //  - 主表字段
        //  - 分类名称 categoryName
        //  - 一起查出来了
        List<SetmealDish> setmealDishes = setmealDishMapper.getBySetmealId(id);// 这个套餐下面到底挂了哪些菜
        setmealVO.setSetmealDishes(setmealDishes);// 设置对应 id 的套餐下对应的菜品，这个VO包含： 套餐主信息 + 分类名 + 套餐和菜品的关系列表
        return setmealVO;
    }
    //### 为什么这种“组装”一般写在 Service 层
    //因为这一步已经不只是“查表”了，而是在做：
    //- 业务返回结构组织

    //Controller 更适合：
    //- 接请求
    //- 返回结果

    //Mapper 更适合：
    //- 查单张表或某类 SQL

    //而 Service 正好适合做这种中间组装：
    //- 先调一个 Mapper
    //- 再调另一个 Mapper
    //- 把结果拼成前端真正需要的结构

    /**
     * 修改套餐和关联菜品
     * @param setmealDTO
     */
    @Override
    @Transactional
    public void updateWithDish(SetmealDTO setmealDTO) {
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);// DTO 转主表对象
        setmealMapper.update(setmeal);// 更新主表,先把套餐自己的基本信息更新掉

        Long setmealId = setmealDTO.getId();
        setmealDishMapper.deleteBySetmealIds(Collections.singletonList(setmealId));// 删除 setmeal_dish 表中对应 id 的旧关系
        //- 接下来，不去逐条比较旧关系和新关系的差异
        //- 而是先把这个套餐原有的所有关联关系全部删掉

        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        if (setmealDishes == null || setmealDishes.isEmpty()) {
            return;
        }
        //保护性判断。
        //从当前业务看，套餐通常应该有菜品，但代码层面先防一下空值更稳。

        setmealDishes.forEach(setmealDish -> setmealDish.setSetmealId(setmealId));
        //因为前端传来的每条 setmealDish 数据里，未必天然就是一个完全适合直接入库的关系对象。
        //所以后端统一把当前套餐 id 再补进去。

        setmealDishMapper.insertBatch(setmealDishes);// 重新插入新关系
    }

    /**
     * 条件查询套餐
     *
     * @param setmeal
     * @return
     */
    @Override
    public List<Setmeal> list(Setmeal setmeal) {
        return setmealMapper.list(setmeal);
    }

    /**
     * 根据套餐 id 查询套餐内菜品项
     *
     * @param id
     * @return
     */
    @Override
    public List<DishItemVO> getDishItemById(Long id) {
        return setmealMapper.getDishItemBySetmealId(id);
    }
}
