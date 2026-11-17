package com.fyreader.service.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;

/**
 * 全局异常处理：把任何异常都收敛为统一 {@link Result} 结构。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleMaxUpload(MaxUploadSizeExceededException e) {
        log.warn("上传文件超过大小限制: {}", e.getMessage());
        return Result.fail(ResultCode.BAD_REQUEST, "上传文件超过大小上限，安装包最大 200MB");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        return Result.fail(ResultCode.BAD_REQUEST, firstFieldError(e.getBindingResult().getFieldError()));
    }

    @ExceptionHandler(BindException.class)
    public Result<Void> handleBind(BindException e) {
        return Result.fail(ResultCode.BAD_REQUEST, firstFieldError(e.getBindingResult().getFieldError()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraint(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .findFirst()
                .map(ConstraintViolation::getMessage)
                .orElse(ResultCode.BAD_REQUEST.getMessage());
        return Result.fail(ResultCode.BAD_REQUEST, message);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return Result.fail(ResultCode.BAD_REQUEST, "缺少参数: " + e.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return Result.fail(ResultCode.BAD_REQUEST, "参数类型不匹配: " + e.getName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException e) {
        return Result.fail(ResultCode.BAD_REQUEST, "请求体格式错误");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return Result.fail(ResultCode.BAD_REQUEST, "不支持的请求方法: " + e.getMethod());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleOther(Exception e) {
        log.error("未捕获异常", e);
        String msg = e.getMessage() == null ? ResultCode.ERROR.getMessage() : e.getMessage();
        return Result.fail(ResultCode.ERROR, msg);
    }

    private String firstFieldError(FieldError fieldError) {
        if (fieldError == null) {
            return ResultCode.BAD_REQUEST.getMessage();
        }
        return fieldError.getField() + ": " + fieldError.getDefaultMessage();
    }
}
