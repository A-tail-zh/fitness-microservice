package com.fitness.userservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理用户未找到异常
     * <p>
     * 当系统中发生UserNotFoundException异常时，该处理器会捕获并返回HTTP 404状态码，
     * 同时在响应体中包含具体的错误信息。
     *
     * @param ex UserNotFoundException异常对象，包含用户未找到的详细错误信息
     * @return ResponseEntity对象，包含HTTP 404状态码和错误信息Map（格式：{"message": "错误信息"}）
     */
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<?> handleUserNotFound(UserNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", ex.getMessage()));
    }


    /**
     * 处理用户已存在异常
     * <p>
     * 当系统中发生UserAlreadyExistsException异常时（如注册时邮箱已被占用），该处理器会捕获并返回HTTP 409状态码，
     * 同时在响应体中包含具体的冲突信息。
     *
     * @param ex UserAlreadyExistsException异常对象，包含用户已存在的详细错误信息
     * @return ResponseEntity对象，包含HTTP 409 CONFLICT状态码和错误信息Map（格式：{"message": "错误信息"}）
     */
    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<?> handleUserAlreadyExists(UserAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", ex.getMessage()));
    }


    @ExceptionHandler(UserGoalNotFoundException.class)
    public ResponseEntity<?> handleUserGoalNotFound(UserGoalNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", ex.getMessage()));
    }


    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(MethodArgumentNotValidException ex) {
        String errors = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));

        return ResponseEntity.badRequest()
                .body(Map.of("message", errors));
    }


    /**
     * 处理其他未分类的异常
     * <p>
     * 作为全局异常的兜底处理器，捕获所有未被其他特定处理器捕获的Exception类型异常。
     * 该处理器会返回HTTP 500状态码，并在响应体中包含错误信息。
     *
     * @param ex Exception异常对象，包含系统发生的未预期错误的详细信息
     * @return ResponseEntity对象，包含HTTP 500 INTERNAL SERVER ERROR状态码和错误信息Map（格式：{"message": "错误信息"}）
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleOther(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", ex.getMessage()));
    }
}