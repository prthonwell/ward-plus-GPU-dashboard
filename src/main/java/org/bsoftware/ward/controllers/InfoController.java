package org.bsoftware.ward.controllers;

import org.bsoftware.ward.services.InfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 机器信息 API：GET /api/info，返回 CPU/内存/磁盘/uptime 等描述性数据（前端每秒刷新）
 *
 * @author Rudolf Barbu
 * @version 1.0.1
 */
@RestController
@RequestMapping(value = "/api/info")
public class InfoController
{
    /**
     * 通过 OSHI 组装 InfoDto
     */
    @Autowired
    private InfoService infoService;

    /**
     * 获取当前服务器静态/半静态信息
     *
     * @return ResponseEntity to servlet
     */
    @GetMapping
    public ResponseEntity<?> getInfo() throws Exception
    {
        return new ResponseEntity<>(infoService.getInfo(), HttpStatus.OK);
    }
}