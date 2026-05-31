package com.sky.context;

public class BaseContext {

    public static ThreadLocal<Long> threadLocal = new ThreadLocal<>();
    //先把它理解成： 给“本次请求”临时存一个当前用户 id 的小盒子
    public static void setCurrentId(Long id) {
        threadLocal.set(id);
    }
    //拦截器校验完 token 后，把当前用户 id 塞进去
    public static Long getCurrentId() {
        return threadLocal.get();
    }

    public static void removeCurrentId() {
        threadLocal.remove();
    }

}
