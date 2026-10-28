package com.wc.controller;

import com.wc.user.ForbiddenException;
import com.wc.user.UnauthorizedException;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
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

    /**
     * 处理 @Valid @RequestBody 校验失败（注册/登录等 JSON 请求体）—— 返回 400。
     * <p>
     * 与 ConstraintViolationException 的区别：后者是方法参数（@RequestParam）上的校验。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<String> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("请求体校验失败: {}", msg);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("参数校验失败: " + msg);
    }

    /**
     * 处理请求体格式错误（JSON 解析失败）—— 返回 400。
     *
     * 不加这个处理的话，Spring 默认会返回自己的 JSON 错误体，
     * 和本项目其它错误的纯文本风格不一致。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<String> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("请求体格式错误: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("参数校验失败: 请求体格式不正确");
    }

    /**
     * 处理未登录 / 登录失效 / 用户名密码错误 —— 返回 401，前端据此跳登录。
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<String> handleUnauthorized(UnauthorizedException ex) {
        log.warn("未登录访问: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ex.getMessage());
    }

    /**
     * 处理越权访问（如删除别人的会话）—— 返回 403。
     */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<String> handleForbidden(ForbiddenException ex) {
        log.warn("越权访问: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ex.getMessage());
    }

    /**
     * 处理业务参数非法（如 chatId 不是 UUID）—— 属于调用方的问题，返回 400。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("参数非法: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("参数非法: " + ex.getMessage());
    }

    /**
     * 处理功能未启用（如未开启记忆持久化时的历史记录接口）—— 返回 503。
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleIllegalState(IllegalStateException ex) {
        log.warn("功能不可用: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ex.getMessage());
    }
}
