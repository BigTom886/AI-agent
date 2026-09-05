package com.wc.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理 —— 把校验异常转成 400，避免落到 Spring 默认的 500 错误页。
 * <p>
 * 业务异常由各 Controller 自行处理（如 MCP 调用失败 → 503）。
 */
@RestControllerAdvice
@Slf4j
@Tag(name = "全局异常处理")
public class GlobalExceptionHandler {

    /**
     * 处理 @Validated + @NotBlank 等参数校验失败。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<String> handleConstraintViolation(ConstraintViolationException ex) {
        String msg = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", msg);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("参数校验失败: " + msg);
    }
}
