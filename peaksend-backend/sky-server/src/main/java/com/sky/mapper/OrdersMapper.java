package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.dto.GoodsSalesDTO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.entity.Orders;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
            "<if test='number != null and number != \"\"'> and number like concat('%', #{number}, '%') </if>",
            "<if test='phone != null and phone != \"\"'> and phone like concat('%', #{phone}, '%') </if>",
            "<if test='userId != null'> and user_id = #{userId} </if>",
            "<if test='status != null'> and status = #{status} </if>",
            "<if test='beginTime != null'> and order_time <![CDATA[ >= ]]> #{beginTime} </if>",
            "<if test='endTime != null'> and order_time <![CDATA[ <= ]]> #{endTime} </if>",
            "</where>",
            "order by order_time desc",
            "</script>"
    })
    Page<Orders> pageQuery(OrdersPageQueryDTO ordersPageQueryDTO);

    /**
     * 根据状态统计订单数量
     *
     * @param status 订单状态
     * @return 数量
     */
    @Select("select count(id) from orders where status = #{status}")
    Integer countStatus(Integer status);

    /**
     * 根据动态条件统计订单数量
     *
     * @param map 条件
     * @return 订单数量
     */
    @Select({
            "<script>",
            "select count(id) from orders",
            "<where>",
            "<if test='begin != null'> and order_time <![CDATA[ >= ]]> #{begin} </if>",
            "<if test='end != null'> and order_time <![CDATA[ <= ]]> #{end} </if>",
            "<if test='status != null'> and status = #{status} </if>",
            "</where>",
            "</script>"
    })
    Integer countByMap(Map<String, Object> map);

    /**
     * 根据动态条件统计营业额
     *
     * @param map 条件
     * @return 营业额
     */
    @Select({
            "<script>",
            "select sum(amount) from orders",
            "<where>",
            "<if test='begin != null'> and order_time <![CDATA[ >= ]]> #{begin} </if>",
            "<if test='end != null'> and order_time <![CDATA[ <= ]]> #{end} </if>",
            "<if test='status != null'> and status = #{status} </if>",
            "</where>",
            "</script>"
    })
    Double sumByMap(Map<String, Object> map);

    /**
     * 统计指定时间范围内销量 top10
     *
     * @param begin 起始时间
     * @param end   结束时间
     * @return 商品销量列表
     */
    @Select({
            "select od.name as name, sum(od.number) as number",
            "from order_detail od",
            "left join orders o on od.order_id = o.id",
            "where o.status = 5",
            "and o.order_time >= #{begin}",
            "and o.order_time <= #{end}",
            "group by od.name",
            "order by number desc",
            "limit 0,10"
    })
    List<GoodsSalesDTO> getSalesTop10(LocalDateTime begin, LocalDateTime end);
}
