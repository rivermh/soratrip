package com.rivermh.soratrip.global.error;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 페이지(Thymeleaf) 요청 전용 예외 처리기.
 * REST API(/api/**) 요청은 {@link ApiExceptionHandler}가 JSON으로 별도 처리한다.
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 404 (페이지 / 리소스 없음) 예외 처리
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public String handle404(NoResourceFoundException e, HttpServletResponse response, Model model) {
        log.warn("404 Not Found: {}", e.getResourcePath());
        return render(response, model, HttpStatus.NOT_FOUND, "error/404");
    }

    /**
     * 403 (접근 권한 없음) 예외 처리 — Spring Security 필터 체인의 인가 실패(URL 매처, CSRF 검증 실패 등)와
     * 서비스 계층에서 명시적으로 던지는 권한 예외를 모두 포괄한다.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public String handle403(AccessDeniedException e, HttpServletResponse response, Model model) {
        log.warn("403 Access Denied: {}", e.getMessage());
        return render(response, model, HttpStatus.FORBIDDEN, "error/403");
    }

    /**
     * 그 외 처리되지 않은 모든 예외 (500 Server Error)
     */
    @ExceptionHandler(Exception.class)
    public String handleGeneralException(Exception e, HttpServletResponse response, Model model) {
        log.error("Unhandled Exception: ", e);
        return render(response, model, HttpStatus.INTERNAL_SERVER_ERROR, "error/500");
    }

    private String render(HttpServletResponse response, Model model, HttpStatus status, String view) {
        response.setStatus(status.value());
        model.addAttribute("status", status.value());
        return view;
    }
}
