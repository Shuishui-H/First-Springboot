package com.erp.demo.settings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.server.ResponseStatusException;

/** 系统设置页面的独立持久化接口，避免浏览器 localStorage 在换设备或清缓存后丢失。 */
@RestController
@Profile("mysql")
@RequestMapping("/api/system-settings")
public class SystemSettingsController {
    private static final String KEY = "system-settings-v1";
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public SystemSettingsController(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('system:settings:manage')")
    public JsonNode get() {
        try {
            String value = jdbcTemplate.queryForObject("SELECT setting_value FROM sys_setting WHERE setting_key = ?", String.class, KEY);
            return objectMapper.readTree(value);
        } catch (EmptyResultDataAccessException ignored) {
            return NullNode.getInstance();
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "系统设置读取失败", exception);
        }
    }

    @PutMapping
    @PreAuthorize("hasAuthority('system:settings:manage')")
    public JsonNode save(@RequestBody JsonNode value) {
        try {
            jdbcTemplate.update("INSERT INTO sys_setting (setting_key, setting_value) VALUES (?, ?) "
                    + "ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value), updated_at = CURRENT_TIMESTAMP",
                    KEY, objectMapper.writeValueAsString(value), objectMapper.writeValueAsString(value));
            return value;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "系统设置保存失败", exception);
        }
    }
}
