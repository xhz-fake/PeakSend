package com.sky.constant;

/**
 * 信息提示常量类
 */
public class MessageConstant {

    public static final String PASSWORD_ERROR = "密码错误";
    public static final String ACCOUNT_NOT_FOUND = "账号不存在";
    public static final String ACCOUNT_LOCKED = "账号被锁定";
    public static final String ALREADY_EXISTS = "用户已存在";
    public static final String UNKNOWN_ERROR = "未知错误";
    public static final String USER_NOT_LOGIN = "用户未登录";
    public static final String CATEGORY_BE_RELATED_BY_SETMEAL = "当前分类关联了套餐,不能删除";
    public static final String CATEGORY_BE_RELATED_BY_DISH = "当前分类关联了菜品,不能删除";
    public static final String SHOPPING_CART_IS_NULL = "购物车数据为空，不能下单";
    public static final String ADDRESS_BOOK_IS_NULL = "用户地址为空，不能下单";
    public static final String LOGIN_FAILED = "登录失败";
    public static final String UPLOAD_FAILED = "文件上传失败";
    public static final String SETMEAL_ENABLE_FAILED = "套餐内包含未启售菜品，无法启售";
    public static final String PASSWORD_EDIT_FAILED = "密码修改失败";
    public static final String DISH_ON_SALE = "起售中的菜品不能删除";
    public static final String SETMEAL_ON_SALE = "起售中的套餐不能删除";
    public static final String DISH_BE_RELATED_BY_SETMEAL = "当前菜品关联了套餐,不能删除";
    public static final String ORDER_STATUS_ERROR = "订单状态错误";
    public static final String ORDER_NOT_FOUND = "订单不存在";
    public static final String FLASH_SALE_ACTIVITY_NOT_FOUND = "限量套餐活动不存在";
    public static final String FLASH_SALE_ACTIVITY_STATUS_INVALID = "限量套餐活动状态非法";
    public static final String FLASH_SALE_ACTIVITY_TIME_INVALID = "限量套餐活动时间配置错误";
    public static final String FLASH_SALE_ACTIVITY_STOCK_INVALID = "限量套餐库存配置错误";
    public static final String FLASH_SALE_ACTIVITY_NOT_STARTED = "限量套餐活动尚未开始";
    public static final String FLASH_SALE_ACTIVITY_ENDED = "限量套餐活动已结束";
    public static final String FLASH_SALE_ACTIVITY_DISABLED = "限量套餐活动未启用";
    public static final String FLASH_SALE_STOCK_NOT_ENOUGH = "限量套餐库存不足";
    public static final String FLASH_SALE_DUPLICATE_ORDER = "同一用户不可重复抢购";
    public static final String FLASH_SALE_SETMEAL_NOT_FOUND = "限量套餐对应的套餐不存在";
    public static final String FLASH_SALE_REDIS_UNAVAILABLE = "Redis 未就绪，暂时无法抢购限量套餐";
    public static final String FLASH_SALE_ACTIVITY_HAS_ORDER_RECORDS = "该活动已有抢购记录，不允许删除";

}
