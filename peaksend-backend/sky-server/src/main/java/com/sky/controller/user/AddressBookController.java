package com.sky.controller.user;

import com.sky.context.BaseContext;
import com.sky.entity.AddressBook;
import com.sky.result.Result;
import com.sky.service.AddressBookService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
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
    @CacheEvict(cacheNames = "addressBookByIdCache", allEntries = true)
    // 地址数据一旦变了，这组缓存就先清掉，避免旧数据残留。
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
    @Cacheable(cacheNames = "addressBookByIdCache", key = "T(com.sky.context.BaseContext).getCurrentId() + ':' + #id")
    //- key 不是只用 id 而是 当前用户id:id

    // 这里缓存的不是单独的 AddressBook，而是整个 Result<AddressBook> 返回结果。
    // 所以就算这次没查到地址，返回的是 Result.success(null)，它也仍然能被短暂缓存下来。
    public Result<AddressBook> getById(@PathVariable Long id) {
        log.info("根据 id 查询地址：userId={}, id={}", BaseContext.getCurrentId(), id);
        return Result.success(addressBookService.getById(id));
    }
    //这个接口就算“没查到地址”，返回的也不是空方法结果，而是一个 data=null 的成功响应对象，所以这个“查不到”的结果也能短暂放进 Redis，避免别人反复查同一个不存在的 id 时每次都打数据库。

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
    @CacheEvict(cacheNames = "addressBookByIdCache", allEntries = true)
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
    @CacheEvict(cacheNames = "addressBookByIdCache", allEntries = true)
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
    @CacheEvict(cacheNames = "addressBookByIdCache", allEntries = true)
    public Result deleteById(Long id) {
        log.info("删除地址：{}", id);
        addressBookService.deleteById(id);
        return Result.success();
    }
}
