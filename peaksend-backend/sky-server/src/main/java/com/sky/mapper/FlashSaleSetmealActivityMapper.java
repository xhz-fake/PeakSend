package com.sky.mapper;

import com.sky.entity.FlashSaleSetmealActivity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface FlashSaleSetmealActivityMapper {

    @Options(useGeneratedKeys = true, keyProperty = "id")
    @Insert("insert into flash_sale_setmeal_activity (setmeal_id, activity_name, setmeal_name, setmeal_price, setmeal_image, stock, status, start_time, end_time, create_time, update_time, create_user, update_user) " +
            "values (#{setmealId}, #{activityName}, #{setmealName}, #{setmealPrice}, #{setmealImage}, #{stock}, #{status}, #{startTime}, #{endTime}, #{createTime}, #{updateTime}, #{createUser}, #{updateUser})")
    void insert(FlashSaleSetmealActivity activity);

    @Select("select * from flash_sale_setmeal_activity where id = #{id}")
    FlashSaleSetmealActivity getById(Long id);

    @Select("select * from flash_sale_setmeal_activity order by create_time desc, id desc")
    List<FlashSaleSetmealActivity> listAll();

    @Select("select * from flash_sale_setmeal_activity where status = 1 and stock > 0 and start_time <= #{now} and end_time >= #{now} order by start_time asc, id desc")
    List<FlashSaleSetmealActivity> listAvailable(LocalDateTime now);

    @Update("update flash_sale_setmeal_activity set status = #{status}, update_time = #{updateTime}, update_user = #{updateUser} where id = #{id}")
    void updateStatus(@Param("id") Long id,
                      @Param("status") Integer status,
                      @Param("updateTime") LocalDateTime updateTime,
                      @Param("updateUser") Long updateUser);

    @Update("update flash_sale_setmeal_activity set stock = stock - 1, update_time = #{updateTime}, update_user = #{updateUser} where id = #{id} and stock > 0")
    int decreaseStock(@Param("id") Long id,
                      @Param("updateTime") LocalDateTime updateTime,
                      @Param("updateUser") Long updateUser);

    @Delete("delete from flash_sale_setmeal_activity where id = #{id}")
    int deleteById(Long id);
}
