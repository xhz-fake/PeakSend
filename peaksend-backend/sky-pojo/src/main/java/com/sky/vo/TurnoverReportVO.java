package com.sky.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 营业额统计数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TurnoverReportVO implements Serializable {

    /**
     * 日期列表，日期之间以逗号分隔
     */
    private String dateList;

    /**
     * 营业额列表，营业额之间以逗号分隔
     */
    private String turnoverList;
}
