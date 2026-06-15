package com.sky.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 商品销量数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoodsSalesDTO implements Serializable {

    /**
     * 商品名称
     */
    private String name;

    /**
     * 销量
     */
    private Integer number;
}
