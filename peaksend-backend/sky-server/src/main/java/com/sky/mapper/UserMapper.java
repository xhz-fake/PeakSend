package com.sky.mapper;

import com.sky.entity.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

/**
 * C端用户 Mapper
 */
@Mapper
public interface UserMapper {

    /**
     * 根据 openid 查询用户
     *
     * @param openid
     * @return
     */
    @Select("select * from user where openid = #{openid}")
    User getByOpenid(String openid);

    /**
     * 根据 id 查询用户
     *
     * @param id
     * @return
     */
    @Select("select * from user where id = #{id}")
    User getById(Long id);

    /**
     * 新增用户
     *
     * @param user
     */
    @Options(useGeneratedKeys = true, keyProperty = "id")
    @Insert("insert into user(openid, create_time) values(#{openid}, #{createTime})")
    void insert(User user);

    /**
     * 根据动态条件统计用户数量
     *
     * @param map 条件
     * @return 用户数量
     */
    @Select({
            "<script>",
            "select count(id) from user",
            "<where>",
            "<if test='begin != null'> and create_time <![CDATA[ >= ]]> #{begin} </if>",
            "<if test='end != null'> and create_time <![CDATA[ <= ]]> #{end} </if>",
            "</where>",
            "</script>"
    })
    Integer countByMap(Map<String, Object> map);
}
