package com.rivermh.soratrip.global.error;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * REST API(@RestController, /api/**) 요청 전용 예외 처리기.
 * fetch/AJAX 호출부가 HTML 오류 페이지 대신 JSON 오류 응답을 받도록
 * 페이지용 {@link GlobalExceptionHandler}보다 우선 적용된다.
 */
@Slf4j
@RestControllerAdvice(annotations = RestController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class ApiExceptionHandler {

    private final MessageSource messageSource;

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handle403(AccessDeniedException e) {
        log.warn("403 Access Denied (API): {}", e.getMessage());
        return respond(HttpStatus.FORBIDDEN, "api.error.forbidden");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneralException(Exception e) {
        log.error("Unhandled Exception (API): ", e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "api.error.internal");
    }

    private ResponseEntity<Map<String, Object>> respond(HttpStatus status, String messageKey) {
        String message = messageSource.getMessage(messageKey, null, LocaleContextHolder.getLocale());
        return ResponseEntity.status(status).body(Map.of("status", status.value(), "message", message));
    }
}
