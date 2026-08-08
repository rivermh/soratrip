package com.rivermh.soratrip.global.error;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 404 (페이지 / 리소스 없음) 예외 처리
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public String handle404(NoResourceFoundException e, Model model) {
        log.warn("404 Not Found: {}", e.getResourcePath());
        model.addAttribute("status", 404);
        model.addAttribute("message", "요청하신 페이지를 찾을 수 없습니다.");
        return "error/error";
    }

    /**
     * 모든 일반 예외 (500 Server Error) 처리
     */
    @ExceptionHandler(Exception.class)
    public String handleGeneralException(Exception e, Model model) {
        log.error("Unhandled Exception: ", e);
        model.addAttribute("status", 500);
        model.addAttribute("message", "서버 처리 중 오류가 발생했습니다.");
        return "error/error";
    }
}