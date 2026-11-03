package org.bsoftware.ward.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * /api/usage 响应：processor / ram / storage / gpu / vram 的 0–100 使用率
 *
 * @author Rudolf Barbu
 * @version 1.0.1
 */
@Getter
@Setter
public class UsageDto
{
    /**
     * Processor usage field
     */
    private int processor;

    /**
     * Ram usage field
     */
    private int ram;

    /**
     * Storage usage field
     */
    private int storage;

    /**
     * NVIDIA GPU 计算利用率（NVML）；无卡时为 0
     */
    private int gpu;

    /**
     * 显存占用率 used/total（NVML）；无卡时为 0
     */
    private int vram;
}