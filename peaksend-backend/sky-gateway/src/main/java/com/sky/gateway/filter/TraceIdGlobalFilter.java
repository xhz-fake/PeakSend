package com.sky.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@Slf4j
public class TraceIdGlobalFilter implements GlobalFilter, Ordered {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String incomingTraceId = exchange.getRequest().getHeaders().getFirst(TRACE_ID_HEADER);
        String traceId = normalizeOrCreate(incomingTraceId);
        long startTime = System.currentTimeMillis();

        ServerHttpRequest mutatedRequest = exchange.getRequest()
                .mutate()
                .headers(httpHeaders -> httpHeaders.set(TRACE_ID_HEADER, traceId))
                .build();

        exchange.getResponse().getHeaders().set(TRACE_ID_HEADER, traceId);
        log.info("gateway request start: traceId={}, method={}, path={}",
                traceId, exchange.getRequest().getMethod(), exchange.getRequest().getURI().getPath());

        return chain.filter(exchange.mutate().request(mutatedRequest).build())
                .doFinally(signalType -> log.info("gateway request end: traceId={}, method={}, path={}, status={}, costMs={}",
                        traceId,
                        exchange.getRequest().getMethod(),
                        exchange.getRequest().getURI().getPath(),
                        exchange.getResponse().getStatusCode(),
                        System.currentTimeMillis() - startTime));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private String normalizeOrCreate(String traceId) {
        if (traceId == null || traceId.trim().isEmpty()) {
            return UUID.randomUUID().toString().replace("-", "");
        }
        return traceId.trim();
    }
}
