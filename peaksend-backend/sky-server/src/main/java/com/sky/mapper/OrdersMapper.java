package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.entity.Orders;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface OrdersMapper {

    /**
     * 新增订单
     *
     * @param orders
     */
    @Options(useGeneratedKeys = true, keyProperty = "id")// - 插入订单后，数据库生成的主键 id，会自动回填到 orders 对象里
    @Insert("insert into orders (number, status, user_id, address_book_id, order_time, checkout_time, pay_method, " +
            "pay_status, amount, remark, phone, address, user_name, consignee, estimated_delivery_time, delivery_status, " +
            "pack_amount, tableware_number, tableware_status) " +
            "values (#{number}, #{status}, #{userId}, #{addressBookId}, #{orderTime}, #{checkoutTime}, #{payMethod}, " +
            "#{payStatus}, #{amount}, #{remark}, #{phone}, #{address}, #{userName}, #{consignee}, #{estimatedDeliveryTime}, " +
            "#{deliveryStatus}, #{packAmount}, #{tablewareNumber}, #{tablewareStatus})")
    void insert(Orders orders);

    /**
     * 根据 id 查询订单
     *
     * @param id
     * @return
     */
    @Select("select * from orders where id = #{id}")
    Orders getById(Long id);

    /**
     * 更新订单
     *
     * @param orders
     */
    @Update({
            "<script>",
            "update orders",
            "<set>",
            "<if test='status != null'> status = #{status}, </if>",
            "<if test='payStatus != null'> pay_status = #{payStatus}, </if>",
            "<if test='payMethod != null'> pay_method = #{payMethod}, </if>",
            "<if test='checkoutTime != null'> checkout_time = #{checkoutTime}, </if>",
            "<if test='cancelReason != null'> cancel_reason = #{cancelReason}, </if>",
            "<if test='cancelTime != null'> cancel_time = #{cancelTime}, </if>",
            "<if test='rejectionReason != null'> rejection_reason = #{rejectionReason}, </if>",
            "<if test='deliveryTime != null'> delivery_time = #{deliveryTime}, </if>",
            "</set>",
            "where id = #{id}",
            "</script>"
    })
    void update(Orders orders);

    /**
     * 根据订单号查询订单
     *
     * @param number 订单号
     * @return 订单
     */
    @Select("select * from orders where number = #{number}")
    Orders getByNumber(String number);

    /**
     * 条件分页查询订单
     *
     * @param ordersPageQueryDTO 查询条件
     * @return 订单分页结果
     */
    @Select({
            "<script>",
            "select * from orders",
            "<where>",
            "<if test='userId != null'> and user_id = #{userId} </if>",
            "<if test='status != null'> and status = #{status} </if>",
            "</where>",
            "order by order_time desc",
            "</script>"
    })
    Page<Orders> pageQuery(OrdersPageQueryDTO ordersPageQueryDTO);
}
