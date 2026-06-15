package com.sky.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 销量 Top10 统计数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesTop10ReportVO implements Serializable {

    /**
     * 商品名称列表，以逗号分隔
     */
    private String nameList;

    /**
     * 销量列表，以逗号分隔
     */
    private String numberList;
}
