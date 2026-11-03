package org.bsoftware.ward.handlers;

import org.bsoftware.ward.components.UtilitiesComponent;
import org.bsoftware.ward.dto.ErrorDto;
import org.bsoftware.ward.exceptions.ApplicationNotSetUpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import java.io.IOException;

/**
 * 全局异常处理：
 * - ApplicationNotSetUpException → 400 JSON（尚未 setup 却访问 API）
 * - MethodArgumentNotValidException → 422 JSON（setup 表单校验失败）
 * - 其它 Exception → 渲染 error/500 页面
 *
 * @author Rudolf Barbu
 * @version 1.0.0
 */
@ControllerAdvice
@Order(value = Ordered.HIGHEST_PRECEDENCE)
public class ControllerExceptionHandler
{
    private static final Logger log = LoggerFactory.getLogger(ControllerExceptionHandler.class);

    /**
     * 读取主题，用于 500 错误页样式
     */
    @Autowired
    private UtilitiesComponent utilitiesComponent;

    /**
     * 未完成初始化却调用 /api/info 或 /api/usage
     */
    @ResponseBody
    @ExceptionHandler(value = ApplicationNotSetUpException.class)
    public ResponseEntity<?> applicationNotSetUpExceptionHandler(ApplicationNotSetUpException applicationNotSetUpException)
    {
        return new ResponseEntity<>(new ErrorDto(applicationNotSetUpException), HttpStatus.BAD_REQUEST);
    }

    /**
     * SetupDto 校验失败（serverName / theme / port 不合法）
     */
    @ResponseBody
    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public ResponseEntity<?> methodArgumentNotValidExceptionHandler(MethodArgumentNotValidException methodArgumentNotValidException)
    {
        return new ResponseEntity<>(new ErrorDto(methodArgumentNotValidException), HttpStatus.UNPROCESSABLE_ENTITY);
    }

    /**
     * 兜底：未专门处理的异常统一展示 500 页
     *
     * @throws IOException if ini file is unreachable
     */
    @ExceptionHandler(value = Exception.class)
    public String exceptionHandler(Exception exception, Model model) throws IOException
    {
        log.error("Unhandled exception, rendering 500 page", exception);
        model.addAttribute("theme", utilitiesComponent.getThemeName());

        return "error/500";
    }
}