package com.sky.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 订单统计数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderReportVO implements Serializable {

    /**
     * 日期列表，以逗号分隔
     */
    private String dateList;

    /**
     * 订单总数
     */
    private Integer totalOrderCount;

    /**
     * 有效订单数
     */
    private Integer validOrderCount;

    /**
     * 订单完成率
     */
    private Double orderCompletionRate;

    /**
     * 订单数列表，以逗号分隔
     */
    private String orderCountList;

    /**
     * 有效订单数列表，以逗号分隔
     */
    private String validOrderCountList;
}
