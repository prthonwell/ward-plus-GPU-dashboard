package org.bsoftware.ward.controllers;

import org.bsoftware.ward.dto.SetupDto;
import org.bsoftware.ward.services.SetupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import javax.validation.Valid;

/**
 * 初始化 API：POST /api/setup，接收 setup 页提交的服务器名 / 主题 / 端口并落盘重启
 *
 * @author Rudolf Barbu
 * @version 1.0.1
 */
@RestController
@RequestMapping(value = "/api/setup")
public class SetupController
{
    /**
     * 写入 setup.ini 并触发应用重启
     */
    @Autowired
    private SetupService setupService;

    /**
     * 校验 SetupDto 后保存配置；成功后前端会跳转到新端口
     *
     * @param setupDto dto with data
     * @return ResponseEntity to servlet
     */
    @PostMapping
    public ResponseEntity<?> postSetup(@RequestBody @Valid SetupDto setupDto) throws Exception
    {
        return new ResponseEntity<>(setupService.postSetup(setupDto), HttpStatus.OK);
    }
}