package com.erp.demo.persistence;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** V3 关系表业务提交拦截器；旧 JSON 快照仅作为迁移备份，不再参与写入。 */
@Configuration
@Profile("mysql")
public class PersistenceWebConfig implements WebMvcConfigurer {
    private final RelationalInventoryProjectionService inventoryProjectionService;

    public PersistenceWebConfig(RelationalInventoryProjectionService inventoryProjectionService) {
        this.inventoryProjectionService = inventoryProjectionService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
                String method = request.getMethod();
                if (exception == null && response.getStatus() >= 200 && response.getStatus() < 300
                        && ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method) || "DELETE".equals(method))) {
                    inventoryProjectionService.synchronize();
                }
            }
        }).addPathPatterns("/api/**");
    }
}
