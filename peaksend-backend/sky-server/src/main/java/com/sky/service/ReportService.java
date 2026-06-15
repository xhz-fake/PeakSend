package com.sky.service;

import com.sky.vo.TurnoverReportVO;
import com.sky.vo.UserReportVO;
import com.sky.vo.OrderReportVO;
import com.sky.vo.SalesTop10ReportVO;

import javax.servlet.http.HttpServletResponse;
import java.time.LocalDate;

public interface ReportService {

    /**
     * 营业额统计
     *
     * @param begin 开始日期
     * @param end   结束日期
     * @return 营业额统计结果
     */
    TurnoverReportVO getTurnoverStatistics(LocalDate begin, LocalDate end);

    /**
     * 用户统计
     *
     * @param begin 开始日期
     * @param end   结束日期
     * @return 用户统计结果
     */
    UserReportVO getUserStatistics(LocalDate begin, LocalDate end);

    /**
     * 订单统计
     *
     * @param begin 开始日期
     * @param end   结束日期
     * @return 订单统计结果
     */
    OrderReportVO getOrdersStatistics(LocalDate begin, LocalDate end);

    /**
     * 销量 Top10 统计
     *
     * @param begin 开始日期
     * @param end   结束日期
     * @return 销量 Top10 统计结果
     */
    SalesTop10ReportVO getSalesTop10(LocalDate begin, LocalDate end);

    /**
     * 导出近30天运营数据报表
     *
     * @param response 响应对象
     */
    void exportBusinessData(HttpServletResponse response);
}
