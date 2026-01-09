package com.agenttrust.platform.web;

import com.agenttrust.platform.web.observability.RequestCorrelationFilter;
import com.agenttrust.platform.web.problem.GlobalProblemHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Auto-configuration for shared web primitives.
 *
 * This ensures services that depend on platform-web automatically get:
 * - RequestCorrelationFilter (traceparent + X-Correlation-Id handling)
 * - GlobalProblemHandler (RFC 9457 Problem Details responses)
 *
 * Note: This class is activated via META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
 * (added as the next Sprint 1 file).
 */
@AutoConfiguration
public class PlatformWebAutoConfiguration {

    @Bean
    public FilterRegistrationBean<RequestCorrelationFilter> requestCorrelationFilter() {
        FilterRegistrationBean<RequestCorrelationFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new RequestCorrelationFilter());
        bean.setName("agenttrustRequestCorrelationFilter");
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        bean.addUrlPatterns("/*");
        return bean;
    }

    @Bean
    public GlobalProblemHandler globalProblemHandler() {
        return new GlobalProblemHandler();
    }
}
