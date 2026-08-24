package com.sky.product.filter;

import com.sky.constant.TraceConstant;
import com.sky.utils.TraceIdUtil;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = TraceIdUtil.normalizeOrCreate(request.getHeader(TraceConstant.TRACE_ID_HEADER));
        long startTime = System.currentTimeMillis();

        //MDC 这个词，先把它理解成：当前线程随身带着的一份日志上下文。
        MDC.put(TraceConstant.TRACE_ID, traceId);
        response.setHeader(TraceConstant.TRACE_ID_HEADER, traceId);

        try {
            log.info("request start: method={}, uri={}", request.getMethod(), request.getRequestURI());
            filterChain.doFilter(request, response);
        } finally {
            log.info("request end: method={}, uri={}, status={}, costMs={}",
                    request.getMethod(), request.getRequestURI(), response.getStatus(),
                    System.currentTimeMillis() - startTime);
            MDC.remove(TraceConstant.TRACE_ID);
        }
    }
}
