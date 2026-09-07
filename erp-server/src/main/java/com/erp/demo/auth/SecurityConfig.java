package com.erp.demo.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpStatus;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, @Value("${erp.auth.enabled:false}") boolean authEnabled) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .exceptionHandling(exception -> exception.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(authorize -> {
                    if (authEnabled) {
                        authorize.requestMatchers("/api/auth/login", "/api/auth/status").permitAll().anyRequest().authenticated();
                    } else {
                        authorize.anyRequest().permitAll();
                    }
                });
        if (!authEnabled) {
            // V1/V2 的无数据库演示保持匿名可用；正式 MySQL profile 不注入任何演示权限。
            http.anonymous(anonymous -> anonymous.authorities(List.of(
                        "base:product:list", "base:product:manage", "base:supplier:list", "base:supplier:manage",
                        "base:customer:list", "base:customer:manage", "base:warehouse:list", "base:warehouse:manage",
                        "purchase:order:list", "purchase:order:create", "purchase:order:approve", "purchase:receipt:list", "purchase:receipt:confirm",
                        "sales:order:list", "sales:order:create", "sales:order:approve", "sales:stockout:list", "sales:stockout:confirm",
                        "sales:return:list", "sales:return:create", "sales:return:approve", "sales:return:confirm",
                        "inventory:balance:view", "inventory:flow:view", "inventory:movement:manage", "inventory:transfer:manage", "inventory:stocktake:manage", "report:view",
                        "system:user:list", "system:user:add", "system:user:edit", "system:user:status", "system:user:password",
                        "system:role:list", "system:role:config", "system:role:status", "system:settings:manage").toArray(new String[0])));
        }
        return http.build();
    }
}
