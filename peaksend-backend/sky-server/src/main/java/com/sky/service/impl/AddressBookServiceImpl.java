package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.entity.AddressBook;
import com.sky.mapper.AddressBookMapper;
import com.sky.service.AddressBookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AddressBookServiceImpl implements AddressBookService {

    @Autowired
    private AddressBookMapper addressBookMapper;

    /**
     * 新增地址
     *
     * @param addressBook
     */
    @Override
    @Transactional
    public void save(AddressBook addressBook) {
        Long userId = BaseContext.getCurrentId();
        addressBook.setUserId(userId);

        List<AddressBook> addressBookList = addressBookMapper.list(AddressBook.builder()
                .userId(userId)
                .build());

        if (addressBook.getIsDefault() != null && addressBook.getIsDefault() == 1) {
            clearDefault(userId);
            // 如果用户新增地址时，主动说“这条就是默认地址”
            //- 那就先清掉旧默认，再把它设成默认
        } else if (addressBookList == null || addressBookList.isEmpty()) {
            addressBook.setIsDefault(1);
            //如果这是用户的第一条地址
            //- 那系统自动把它设成默认
        } else {
            addressBook.setIsDefault(0);
            //否则默认就是：非默认地址
        }

        addressBookMapper.insert(addressBook);
    }

    /**
     * 查询当前用户的地址列表
     *
     * @return
     */
    @Override
    public List<AddressBook> list() {
        return addressBookMapper.list(AddressBook.builder()
                .userId(BaseContext.getCurrentId())
                .build());
    }

    /**
     * 根据 id 查询地址
     *
     * @param id
     * @return
     */
    @Override
    public AddressBook getById(Long id) {
        AddressBook addressBook = addressBookMapper.getById(id);
        if (addressBook == null) {
            return null;
        }
        return addressBook.getUserId().equals(BaseContext.getCurrentId()) ? addressBook : null;
    }

    /**
     * 修改地址
     *
     * @param addressBook
     */
    @Override
    @Transactional
    public void update(AddressBook addressBook) {
        Long userId = BaseContext.getCurrentId();
        addressBook.setUserId(userId);
        if (addressBook.getIsDefault() != null && addressBook.getIsDefault() == 1) {
            clearDefault(userId);
        }
        addressBookMapper.update(addressBook);
    }

    /**
     * 设置默认地址
     *
     * @param addressBook
     */
    @Override
    @Transactional// 原子性，为了保证“默认地址切换”这个动作的完整性。
    public void setDefault(AddressBook addressBook) {
        Long userId = BaseContext.getCurrentId();
        clearDefault(userId);
        addressBook.setUserId(userId);
        addressBook.setIsDefault(1);//- 先把当前用户所有地址的默认标记清掉，再把这次选中的地址设成默认
        addressBookMapper.update(addressBook);
    }

    /**
     * 查询默认地址
     *
     * @return
     */
    @Override
    public AddressBook getDefault() {
        List<AddressBook> addressBookList = addressBookMapper.list(AddressBook.builder()
                .userId(BaseContext.getCurrentId())
                .isDefault(1)
                .build());
        if (addressBookList == null || addressBookList.isEmpty()) {
            return null;
        }
        return addressBookList.get(0);
    }

    /**
     * 删除地址
     *
     * @param id
     */
    @Override
    @Transactional
    public void deleteById(Long id) {
        Long userId = BaseContext.getCurrentId();
        AddressBook addressBook = addressBookMapper.getById(id);
        if (addressBook == null || !addressBook.getUserId().equals(userId)) {
            return;
        }

        addressBookMapper.deleteById(AddressBook.builder()
                .id(id)
                .userId(userId)
                .build());

        if (addressBook.getIsDefault() != null && addressBook.getIsDefault() == 1) {
            List<AddressBook> remainList = addressBookMapper.list(AddressBook.builder()
                    .userId(userId)
                    .build());
            if (remainList != null && !remainList.isEmpty()) {
                AddressBook nextDefault = remainList.get(0);
                nextDefault.setUserId(userId);
                nextDefault.setIsDefault(1);
                clearDefault(userId);
                addressBookMapper.update(nextDefault);
            }
        }
    }

    private void clearDefault(Long userId) {
        addressBookMapper.updateIsDefaultByUserId(AddressBook.builder()
                .userId(userId)
                .isDefault(0)
                .build());
    }
}
