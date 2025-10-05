package com.enterprise.documentsearch.interceptor;

import com.enterprise.documentsearch.config.ResilienceConfig;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Rate limiting interceptor to handle enterprise-scale load
 * and prevent system overload from too many concurrent requests.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitingInterceptor implements HandlerInterceptor {

    private final ResilienceConfig resilienceConfig;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String tenantId = extractTenantId(request);
        String endpoint = request.getRequestURI();
        
        // Check tenant-specific rate limit
        Bucket tenantBucket = resilienceConfig.getTenantBucket(tenantId);
        ConsumptionProbe tenantProbe = tenantBucket.tryConsumeAndReturnRemaining(1);
        
        if (!tenantProbe.isConsumed()) {
            log.warn("Rate limit exceeded for tenant: {} on endpoint: {}", tenantId, endpoint);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("X-Rate-Limit-Retry-After-Seconds", 
                    String.valueOf(tenantProbe.getNanosToWaitForRefill() / 1_000_000_000));
            response.getWriter().write("{\"error\":\"Rate limit exceeded for tenant\",\"retryAfter\":" + 
                    (tenantProbe.getNanosToWaitForRefill() / 1_000_000_000) + "}");
            return false;
        }
        
        // Check API endpoint-specific rate limit
        Bucket apiBucket = resilienceConfig.getApiBucket(endpoint);
        ConsumptionProbe apiProbe = apiBucket.tryConsumeAndReturnRemaining(1);
        
        if (!apiProbe.isConsumed()) {
            log.warn("API rate limit exceeded for endpoint: {} by tenant: {}", endpoint, tenantId);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("X-Rate-Limit-Retry-After-Seconds", 
                    String.valueOf(apiProbe.getNanosToWaitForRefill() / 1_000_000_000));
            response.getWriter().write("{\"error\":\"API rate limit exceeded\",\"retryAfter\":" + 
                    (apiProbe.getNanosToWaitForRefill() / 1_000_000_000) + "}");
            return false;
        }
        
        // Add rate limit headers for client awareness
        response.setHeader("X-Rate-Limit-Tenant-Remaining", String.valueOf(tenantProbe.getRemainingTokens()));
        response.setHeader("X-Rate-Limit-API-Remaining", String.valueOf(apiProbe.getRemainingTokens()));
        
        log.debug("Request allowed for tenant: {} on endpoint: {}, tenant tokens remaining: {}, api tokens remaining: {}", 
                tenantId, endpoint, tenantProbe.getRemainingTokens(), apiProbe.getRemainingTokens());
        
        return true;
    }

    /**
     * Extract tenant ID from request headers or path
     */
    private String extractTenantId(HttpServletRequest request) {
        // First try to get from header
        String tenantId = request.getHeader("X-Tenant-ID");
        
        if (tenantId == null || tenantId.trim().isEmpty()) {
            // Try to extract from JWT token or path parameter
            String path = request.getRequestURI();
            if (path.contains("/tenant/")) {
                String[] pathParts = path.split("/");
                for (int i = 0; i < pathParts.length - 1; i++) {
                    if ("tenant".equals(pathParts[i]) && i + 1 < pathParts.length) {
                        tenantId = pathParts[i + 1];
                        break;
                    }
                }
            }
        }
        
        // Default tenant if none found
        return tenantId != null ? tenantId : "default";
    }
}