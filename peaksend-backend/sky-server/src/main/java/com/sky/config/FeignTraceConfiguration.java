package com.sky.config;

import com.sky.constant.TraceConstant;
import feign.RequestInterceptor;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignTraceConfiguration {

    @Bean
    public RequestInterceptor traceIdRequestInterceptor() {
        return requestTemplate -> {
            String traceId = MDC.get(TraceConstant.TRACE_ID);
            if (traceId != null && !traceId.isEmpty()) {
                requestTemplate.header(TraceConstant.TRACE_ID_HEADER, traceId);
            }
        };
    }
}
