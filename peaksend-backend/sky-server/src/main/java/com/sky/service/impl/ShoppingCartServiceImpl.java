package com.sky.service.impl;

import com.sky.client.ProductClient;
import com.sky.context.BaseContext;
import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.ShoppingCart;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.service.ShoppingCartService;
import com.sky.vo.DishVO;
import com.sky.vo.SetmealVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ShoppingCartServiceImpl implements ShoppingCartService {

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;

    @Autowired
    private ProductClient productClient;

    /**
     * 添加购物车
     *
     * @param shoppingCartDTO
     */
    @Override
    public void addShoppingCart(ShoppingCartDTO shoppingCartDTO) {
        ShoppingCart shoppingCart = new ShoppingCart();// 更像是一个 查询条件载体
        BeanUtils.copyProperties(shoppingCartDTO, shoppingCart);// 转换为实体类对象
        shoppingCart.setUserId(BaseContext.getCurrentId());// 再把当前登录用户的 userId 补进去，这样查的就是“当前这个用户自己的购物车”

        List<ShoppingCart> list = shoppingCartMapper.list(shoppingCart);
        if (list != null && !list.isEmpty()) {// 如果查到了，说明这次加购的不是“全新的一项”，而是购物车里已经有同款
            ShoppingCart cart = list.get(0);// 取出查到的那条已有记录
            cart.setNumber(cart.getNumber() + 1);// 数量加一
            shoppingCartMapper.updateNumberById(cart);// 更新数据库
            return;// 到这里直接结束，不再往下执行“新增购物车记录”的逻辑
        }

        if (shoppingCartDTO.getDishId() != null) {// 先判断这次加购的是不是菜品
            DishVO dish = productClient.getDishById(shoppingCartDTO.getDishId());// Day18 开始，这里改成跨服务查商品信息
            //在业务里像调本地方法一样调用

            //你以为你在调一个 Java 方法，实际上框架在背后做了这几步：
            //发现你调的是 ProductClient
            //发现它标了 @FeignClient(name = "product-service")于是去找名叫 product-service 的服务实例

            //组装 HTTP 请求：
            //GET /rpc/products/dishes/{id}
            //发给 product-service
            //把返回 JSON 反序列化成 DishVO

            if (dish == null) {
                throw new ShoppingCartBusinessException("菜品不存在，无法加入购物车");
            }
            shoppingCart.setName(dish.getName());
            shoppingCart.setImage(dish.getImage());
            shoppingCart.setAmount(dish.getPrice());
            //确定类型后，后端还要去菜品表或套餐表查询名称、图片、价格等完整信息，因为购物车表落库时不能只保存一个 id。
        } else {// 如果不是菜品，那就是套餐
            SetmealVO setmeal = productClient.getSetmealById(shoppingCartDTO.getSetmealId());
            if (setmeal == null) {
                throw new ShoppingCartBusinessException("套餐不存在，无法加入购物车");
            }
            shoppingCart.setName(setmeal.getName());
            shoppingCart.setImage(setmeal.getImage());
            shoppingCart.setAmount(setmeal.getPrice());
        }

        shoppingCart.setNumber(1);// 因为这是第一次插入，所以数量初始化为 1
        shoppingCart.setCreateTime(LocalDateTime.now());// 记录创建时间
        shoppingCartMapper.insert(shoppingCart);// 真正把这条新购物车记录插进数据库
    }

    /**
     * 查看购物车
     *
     * @return
     */
    @Override
    public List<ShoppingCart> showShoppingCart() {
        ShoppingCart shoppingCart = ShoppingCart.builder()
                .userId(BaseContext.getCurrentId())
                .build();
        return shoppingCartMapper.list(shoppingCart);
    }

    /**
     * 清空购物车
     */
    @Override
    public void cleanShoppingCart() {
        shoppingCartMapper.deleteByUserId(BaseContext.getCurrentId());
    }

    /**
     * 删除购物车中一个商品
     *
     * @param shoppingCartDTO
     */
    @Override
    public void subShoppingCart(ShoppingCartDTO shoppingCartDTO) {
        ShoppingCart shoppingCart = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO, shoppingCart);// 先把前端传来的菜品/套餐/口味条件拷进来
        shoppingCart.setUserId(BaseContext.getCurrentId());// 再补上当前用户 id，确保减的是“当前这个人的购物车”

        List<ShoppingCart> list = shoppingCartMapper.list(shoppingCart);// 还是先按 用户 + 菜品/套餐 + 口味 去查这条购物车项
        if (list == null || list.isEmpty()) {// 如果根本没查到，说明购物车里没有这一项，那就直接结束，避免空指针和误操作
            return;
        }

        ShoppingCart cart = list.get(0);// 查到了，就取出这一条已有记录
        if (cart.getNumber() == 1) {// 这里是最关键的业务判断：如果当前数量只剩 1
            shoppingCartMapper.deleteById(cart.getId());// 那这次减购后数量就该变成 0 ，系统不会把购物车项保留成 0 ，而是直接删掉这条记录
            return;
        }
        //- 购物车里不会长期保存 number = 0 的脏数据
        //- 所以“减到最后一份”不是更新成 0
        //- 而是直接 delete

        cart.setNumber(cart.getNumber() - 1);// 只有当数量大于 1 时，才做减一
        shoppingCartMapper.updateNumberById(cart);// 然后更新数据库
    }
}
