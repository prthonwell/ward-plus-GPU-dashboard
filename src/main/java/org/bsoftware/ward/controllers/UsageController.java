package org.bsoftware.ward.controllers;

import org.bsoftware.ward.exceptions.ApplicationNotSetUpException;
import org.bsoftware.ward.services.UsageHistoryService;
import org.bsoftware.ward.services.UsageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.io.IOException;

/**
 * 资源占用 API：
 * - GET /api/usage：当前瞬时值
 * - GET /api/usage/history?range=10m|1h|3h：磁盘 jsonl 历史
 */
@RestController
@RequestMapping(value = "/api/usage")
public class UsageController
{
    @Autowired
    private UsageService usageService;

    @Autowired
    private UsageHistoryService usageHistoryService;

    /**
     * 获取当前 processor / ram / storage / gpu / vram 使用率
     */
    @GetMapping
    public ResponseEntity<?> getUsage() throws ApplicationNotSetUpException
    {
        return new ResponseEntity<>(usageService.getUsage(), HttpStatus.OK);
    }

    /**
     * 读取历史采样；默认 range=10m
     */
    @GetMapping(value = "/history")
    public ResponseEntity<?> getHistory(@RequestParam(value = "range", defaultValue = "10m") String range)
            throws ApplicationNotSetUpException, IOException
    {
        return new ResponseEntity<>(usageHistoryService.getHistory(range), HttpStatus.OK);
    }
}
