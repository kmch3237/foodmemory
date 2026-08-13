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
     * 권한이 없는 요청. 403 으로 응답한다.
     *
     * 404 가 아니라 403 으로 답하는 이유:
     *   404 로 감추면 "그런 게시물이 있는지" 조차 숨길 수 있어 더 안전하다는 견해도 있다.
     *   다만 이 서비스는 갤러리와 상세를 누구나 볼 수 있어서, 게시물의 존재는 이미 공개돼 있다.
     *   숨길 것이 없는데 404 를 주면 사용자만 헷갈린다.
     */
    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleForbidden(ForbiddenException e, Model model) {
        log.warn("권한 없는 요청: {}", e.getMessage());
        model.addAttribute("message", e.getMessage());
        return "error/403";
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

    /**
     * 서버가 준비되지 않아 처리할 수 없는 경우. 500 으로 응답한다.
     *
     * 예) 카카오 API 키가 설정되지 않은 채로 식당 검색을 요청한 경우.
     * 사용자가 잘못 누른 것이 아니라 서버 설정이 빠진 것이므로 400 이 아니라 500 이고,
     * 로그도 warn 이 아니라 error 로 남겨 운영자가 알아채게 한다.
     */
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleIllegalState(IllegalStateException e, Model model) {
        log.error("서버 상태 오류: {}", e.getMessage());
        model.addAttribute("message", e.getMessage());
        return "error/500";
    }
}
