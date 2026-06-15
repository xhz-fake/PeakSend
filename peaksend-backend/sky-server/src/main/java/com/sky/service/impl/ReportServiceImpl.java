package com.sky.service.impl;

import com.sky.dto.GoodsSalesDTO;
import com.sky.entity.Orders;
import com.sky.mapper.OrdersMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.WorkspaceService;
import com.sky.vo.BusinessDataVO;
import com.sky.vo.SalesTop10ReportVO;
import com.sky.service.ReportService;
import com.sky.vo.OrderReportVO;
import com.sky.vo.TurnoverReportVO;
import com.sky.vo.UserReportVO;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReportServiceImpl implements ReportService {

    @Resource
    private OrdersMapper ordersMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private WorkspaceService workspaceService;

    //这 4 个方法表面不同，骨架其实只有两种
    //- 第一种骨架： 按天循环统计
    //  - 营业额统计 getTurnoverStatistics
    //  - 用户统计 getUserStatistics
    //  - 订单统计 getOrdersStatistics
    //- 第二种骨架： 整段时间直接聚合
    //  - 销量 Top10 getSalesTop10

    @Override
    public TurnoverReportVO getTurnoverStatistics(LocalDate begin, LocalDate end) {
        List<String> dateList = new ArrayList<>();// 这个列表装的是横轴日期，比如：
        List<String> turnoverList = new ArrayList<>();// 这个列表装的是纵轴营业额，比如：

        LocalDate current = begin;
        while (!current.isAfter(end)) {
            //- 不要一口气算整个区间。
            //- 而是把 begin ~ end 拆成一天一天。
            //- 每一天单独查一次。
            //- 再把每天结果拼成前端折线图能直接吃的数据。
            dateList.add(current.toString());
            // - 先把当天日期记下来, 因为前端画图不仅要数值，还要横轴日期

            LocalDateTime beginTime = current.atStartOfDay();
            //这句把就是把 2026-06-02 变成：2026-06-02 00:00:00
            LocalDateTime endTime = current.atTime(LocalTime.MAX);
            //这句把就是把 2026-06-02 变成：2026-06-02 23:59:59.999999999

            Map<String, Object> map = new HashMap<>();
            map.put("begin", beginTime);
            map.put("end", endTime);
            map.put("status", Orders.COMPLETED);
            //- 我要统计这一天的数据
            //- 时间范围就是今天的开始到今天的结束
            //- 而且只统计 已完成订单

            Double turnover = ordersMapper.sumByMap(map);// 真正查数据库
            turnoverList.add(String.valueOf(turnover == null ? 0.0 : turnover));
            //- 如果某一天没有完成订单
            //- sum(amount) 结果不是 0 而是 null
            //  所以这里必须手动兜底成 0.0 ，否则前端图表数据会出问题。

            current = current.plusDays(1);
            //- 今天统计完了，指针往后挪一天
            //- 继续下一天， 直到整段时间跑完
        }

        return TurnoverReportVO.builder()
                .dateList(String.join(",", dateList))
                .turnoverList(String.join(",", turnoverList))
                .build();
    }

    @Override
    public UserReportVO getUserStatistics(LocalDate begin, LocalDate end) {
        List<String> dateList = new ArrayList<>();
        List<String> newUserList = new ArrayList<>();// 装每天新增用户数
        List<String> totalUserList = new ArrayList<>();// 装截止当天的累计用户总数

        LocalDate current = begin;
        while (!current.isAfter(end)) {
            dateList.add(current.toString());

            LocalDateTime beginTime = current.atStartOfDay();
            LocalDateTime endTime = current.atTime(LocalTime.MAX);

            Map<String, Object> map = new HashMap<>();
            map.put("begin", beginTime);
            map.put("end", endTime);
            Integer newUsers = userMapper.countByMap(map);
            newUserList.add(String.valueOf(newUsers == null ? 0 : newUsers));

            map.put("begin", null);
            Integer totalUsers = userMapper.countByMap(map);
            totalUserList.add(String.valueOf(totalUsers == null ? 0 : totalUsers));

            current = current.plusDays(1);
        }

        return UserReportVO.builder()
                .dateList(String.join(",", dateList))
                .newUserList(String.join(",", newUserList))
                .totalUserList(String.join(",", totalUserList))
                .build();
    }

    @Override
    public OrderReportVO getOrdersStatistics(LocalDate begin, LocalDate end) {
        List<String> dateList = new ArrayList<>();
        List<String> orderCountList = new ArrayList<>();
        List<String> validOrderCountList = new ArrayList<>();

        Integer totalOrderCount = 0;
        Integer validOrderCount = 0;

        LocalDate current = begin;
        while (!current.isAfter(end)) {
            dateList.add(current.toString());

            LocalDateTime beginTime = current.atStartOfDay();
            LocalDateTime endTime = current.atTime(LocalTime.MAX);

            Map<String, Object> map = new HashMap<>();
            map.put("begin", beginTime);
            map.put("end", endTime);

            Integer orderCount = ordersMapper.countByMap(map);
            orderCount = orderCount == null ? 0 : orderCount;
            orderCountList.add(String.valueOf(orderCount));
            totalOrderCount += orderCount;

            map.put("status", Orders.COMPLETED);
            Integer dailyValidOrderCount = ordersMapper.countByMap(map);
            dailyValidOrderCount = dailyValidOrderCount == null ? 0 : dailyValidOrderCount;
            validOrderCountList.add(String.valueOf(dailyValidOrderCount));
            validOrderCount += dailyValidOrderCount;

            current = current.plusDays(1);
        }

        Double orderCompletionRate = 0.0;
        if (totalOrderCount > 0) {
            orderCompletionRate = validOrderCount.doubleValue() / totalOrderCount;
        }

        return OrderReportVO.builder()
                .dateList(String.join(",", dateList))
                .totalOrderCount(totalOrderCount)
                .validOrderCount(validOrderCount)
                .orderCompletionRate(orderCompletionRate)
                .orderCountList(String.join(",", orderCountList))
                .validOrderCountList(String.join(",", validOrderCountList))
                .build();
    }

    @Override
    public SalesTop10ReportVO getSalesTop10(LocalDate begin, LocalDate end) {
        LocalDateTime beginTime = begin.atStartOfDay();
        LocalDateTime endTime = end.atTime(LocalTime.MAX);

        List<GoodsSalesDTO> salesTop10 = ordersMapper.getSalesTop10(beginTime, endTime);

        String nameList = salesTop10.stream()
                .map(GoodsSalesDTO::getName)
                .collect(Collectors.joining(","));
        String numberList = salesTop10.stream()
                .map(item -> String.valueOf(item.getNumber()))
                .collect(Collectors.joining(","));

        return SalesTop10ReportVO.builder()
                .nameList(nameList)
                .numberList(numberList)
                .build();
    }

    @Override
    public void exportBusinessData(HttpServletResponse response) {
        LocalDate end = LocalDate.now().minusDays(1);
        LocalDate begin = end.minusDays(29);
        BusinessDataVO overview = workspaceService.getBusinessData(begin.atStartOfDay(), end.atTime(LocalTime.MAX));

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=operation-report.xlsx");

        try (Workbook workbook = new XSSFWorkbook();
             ServletOutputStream outputStream = response.getOutputStream()) {
            Sheet sheet = workbook.createSheet("运营数据报表");
            sheet.setDefaultColumnWidth(18);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 5));

            setCellValue(sheet.createRow(0).createCell(0), "运营数据报表");
            setCellValue(sheet.createRow(1).createCell(0), "时间：" + begin + " 至 " + end);

            Row summaryHeaderRow = sheet.createRow(3);
            setCellValue(summaryHeaderRow.createCell(0), "营业额");
            setCellValue(summaryHeaderRow.createCell(1), formatAmount(overview.getTurnover()));
            setCellValue(summaryHeaderRow.createCell(2), "订单完成率");
            setCellValue(summaryHeaderRow.createCell(3), formatPercent(overview.getOrderCompletionRate()));
            setCellValue(summaryHeaderRow.createCell(4), "新增用户数");
            setCellValue(summaryHeaderRow.createCell(5), String.valueOf(overview.getNewUsers()));

            Row summaryValueRow = sheet.createRow(4);
            setCellValue(summaryValueRow.createCell(0), "有效订单数");
            setCellValue(summaryValueRow.createCell(1), String.valueOf(overview.getValidOrderCount()));
            setCellValue(summaryValueRow.createCell(2), "平均客单价");
            setCellValue(summaryValueRow.createCell(3), formatAmount(overview.getUnitPrice()));

            Row detailHeaderRow = sheet.createRow(6);
            setCellValue(detailHeaderRow.createCell(0), "日期");
            setCellValue(detailHeaderRow.createCell(1), "营业额");
            setCellValue(detailHeaderRow.createCell(2), "有效订单数");
            setCellValue(detailHeaderRow.createCell(3), "订单完成率");
            setCellValue(detailHeaderRow.createCell(4), "平均客单价");
            setCellValue(detailHeaderRow.createCell(5), "新增用户数");

            for (int i = 0; i < 30; i++) {
                LocalDate current = begin.plusDays(i);
                BusinessDataVO dailyData = workspaceService.getBusinessData(current.atStartOfDay(), current.atTime(LocalTime.MAX));
                Row detailRow = sheet.createRow(7 + i);
                setCellValue(detailRow.createCell(0), current.toString());
                setCellValue(detailRow.createCell(1), formatAmount(dailyData.getTurnover()));
                setCellValue(detailRow.createCell(2), String.valueOf(dailyData.getValidOrderCount()));
                setCellValue(detailRow.createCell(3), formatPercent(dailyData.getOrderCompletionRate()));
                setCellValue(detailRow.createCell(4), formatAmount(dailyData.getUnitPrice()));
                setCellValue(detailRow.createCell(5), String.valueOf(dailyData.getNewUsers()));
            }

            workbook.write(outputStream);
            outputStream.flush();
        } catch (IOException e) {
            throw new RuntimeException("导出运营数据报表失败", e);
        }
    }

    private void setCellValue(Cell cell, String value) {
        cell.setCellValue(value);
    }

    private String formatAmount(Double value) {
        DecimalFormat decimalFormat = new DecimalFormat("0.00");
        return decimalFormat.format(value == null ? 0.0 : value);
    }

    private String formatPercent(Double value) {
        DecimalFormat decimalFormat = new DecimalFormat("0.00%");
        return decimalFormat.format(value == null ? 0.0 : value);
    }
}
