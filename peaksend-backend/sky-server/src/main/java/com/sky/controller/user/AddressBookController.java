package com.sky.controller.user;

import com.sky.entity.AddressBook;
import com.sky.result.Result;
import com.sky.service.AddressBookService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户端地址簿相关接口
 */
@RestController
@RequestMapping("/user/addressBook")
@Slf4j
@Api(tags = "用户端地址簿相关接口")
public class AddressBookController {

    @Autowired
    private AddressBookService addressBookService;

    /**
     * 新增地址
     *
     * @param addressBook
     * @return
     */
    @PostMapping
    @ApiOperation("新增地址")
    public Result save(@RequestBody AddressBook addressBook) {
        log.info("新增地址：{}", addressBook);
        addressBookService.save(addressBook);
        return Result.success();
    }

    /**
     * 查询当前用户地址列表
     *
     * @return
     */
    @GetMapping("/list")
    @ApiOperation("查询当前用户地址列表")
    public Result<List<AddressBook>> list() {
        return Result.success(addressBookService.list());
    }

    /**
     * 根据 id 查询地址
     *
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    @ApiOperation("根据 id 查询地址")
    public Result<AddressBook> getById(@PathVariable Long id) {
        return Result.success(addressBookService.getById(id));
    }

    /**
     * 查询默认地址
     *
     * @return
     */
    @GetMapping("/default")
    @ApiOperation("查询默认地址")
    public Result<AddressBook> getDefault() {
        return Result.success(addressBookService.getDefault());
    }

    /**
     * 设置默认地址
     *
     * @param addressBook
     * @return
     */
    @PutMapping("/default")
    @ApiOperation("设置默认地址")
    public Result setDefault(@RequestBody AddressBook addressBook) {
        log.info("设置默认地址：{}", addressBook);
        addressBookService.setDefault(addressBook);
        return Result.success();
    }

    /**
     * 修改地址
     *
     * @param addressBook
     * @return
     */
    @PutMapping
    @ApiOperation("修改地址")
    public Result update(@RequestBody AddressBook addressBook) {
        log.info("修改地址：{}", addressBook);
        addressBookService.update(addressBook);
        return Result.success();
    }

    /**
     * 根据 id 删除地址
     *
     * @param id
     * @return
     */
    @DeleteMapping
    @ApiOperation("根据 id 删除地址")
    public Result deleteById(Long id) {
        log.info("删除地址：{}", id);
        addressBookService.deleteById(id);
        return Result.success();
    }
}
