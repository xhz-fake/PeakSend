package com.sky.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 封装分页查询结果
 */
@Data // 会自动生成 getter/setter/toString/equals/hashCode
@AllArgsConstructor// 这个注解是 Lombok 提供的, 它会在编译期自动帮我们生成“全参数构造器”
@NoArgsConstructor// 这个注解是 Lombok 提供的, 会自动生成无参构造器
public class PageResult implements Serializable {

    private long total; //总记录数

    private List records; //当前页数据集合

}
//- Lombok 是一个“减少样板代码”的工具
//- 它最常见的作用就是少写这些重复代码：
//  - 构造器
//  - getter/setter
//  - toString
//  - equals/hashCode

//- 你项目里其实已经大量在用它了，比如很多实体类上的：
//  - @Data
//  - @Builder
//  - @NoArgsConstructor
//  - @AllArgsConstructor