package com.sky.mapper;

import com.sky.entity.FlashSaleSetmealOrder;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FlashSaleSetmealOrderMapper {

    @Options(useGeneratedKeys = true, keyProperty = "id")
    @Insert("insert into flash_sale_setmeal_order (activity_id, user_id, setmeal_id, order_no, activity_name, setmeal_name, setmeal_price, setmeal_image, status, create_time) " +
            "values (#{activityId}, #{userId}, #{setmealId}, #{orderNo}, #{activityName}, #{setmealName}, #{setmealPrice}, #{setmealImage}, #{status}, #{createTime})")
    void insert(FlashSaleSetmealOrder order);

    @Select("select * from flash_sale_setmeal_order where activity_id = #{activityId} and user_id = #{userId} limit 1")
    FlashSaleSetmealOrder getByActivityIdAndUserId(Long activityId, Long userId);

    @Select("select user_id from flash_sale_setmeal_order where activity_id = #{activityId}")
    List<Long> listUserIdsByActivityId(Long activityId);

    @Select("select count(1) from flash_sale_setmeal_order where activity_id = #{activityId}")
    Integer countByActivityId(Long activityId);

    @Select("select * from flash_sale_setmeal_order where user_id = #{userId} order by create_time desc, id desc")
    List<FlashSaleSetmealOrder> listByUserId(Long userId);
}
