package com.foodmemory.app.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 컨트롤러에서 처리하지 못한 예외를 한곳에서 받는다.
 *
 * @ControllerAdvice 는 모든 컨트롤러에 공통으로 적용되는 처리기다.
 * 이게 없으면 예외가 그대로 올라가 Spring 기본 오류 화면(500)이 노출된다.
 * 사용자에게는 무슨 일인지 알 수 없는 화면이고, 내부 정보가 드러날 수도 있다.
 *
 * 각 컨트롤러마다 try-catch 를 쓰지 않는 이유:
 *   같은 처리가 컨트롤러 수만큼 반복되고, 한 군데라도 빠뜨리면 그 지점만 500 이 된다.
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 없는 자원을 요청한 경우. 404 로 응답한다.
     *
     * 로그는 warn 으로 남긴다. 서버 잘못이 아니라 잘못된 주소로 들어온 것이므로
     * error 로 남기면 진짜 장애가 묻힌다.
     */
    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(NotFoundException e, Model model) {
        log.warn("존재하지 않는 자원 요청: {}", e.getMessage());
        model.addAttribute("message", e.getMessage());
        return "error/404";
    }

    /**
     * 잘못된 입력. 400 으로 응답한다.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleBadRequest(IllegalArgumentException e, Model model) {
        log.warn("잘못된 요청: {}", e.getMessage());
        model.addAttribute("message", e.getMessage());
        return "error/400";
    }
}
