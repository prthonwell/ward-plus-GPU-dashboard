package org.bsoftware.ward.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 单条使用率历史采样点（写入 usage-history.jsonl 的一行）
 */
@Getter
@Setter
public class UsageHistoryPointDto
{
    /**
     * Unix 毫秒时间戳
     */
    private long timestamp;

    private int processor;
    private int ram;
    private int storage;
    private int gpu;
    private int vram;
}
