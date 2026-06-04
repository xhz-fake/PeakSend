package com.sky.mapper;

import com.sky.entity.AddressBook;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AddressBookMapper {

    /**
     * 新增地址
     *
     * @param addressBook
     */
    void insert(AddressBook addressBook);

    /**
     * 条件查询地址
     *
     * @param addressBook
     * @return
     */
    List<AddressBook> list(AddressBook addressBook);

    /**
     * 根据 id 查询地址
     *
     * @param id
     * @return
     */
    @Select("select * from address_book where id = #{id}")
    AddressBook getById(Long id);

    /**
     * 修改地址
     *
     * @param addressBook
     */
    void update(AddressBook addressBook);

    /**
     * 将当前用户的地址默认标记统一改成指定值
     *
     * @param addressBook
     */
    @Update("update address_book set is_default = #{isDefault} where user_id = #{userId}")
    void updateIsDefaultByUserId(AddressBook addressBook);

    /**
     * 根据 id 删除地址
     *
     * @param addressBook
     */
    void deleteById(AddressBook addressBook);
}
