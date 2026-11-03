package org.bsoftware.ward.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * GPU 静态信息：名称、驱动、显存总量、是否可用（NVML）
 */
@Getter
@Setter
public class GpuDto
{
    /**
     * NVML 是否可用且至少有一张 NVIDIA GPU
     */
    private boolean available;

    /**
     * GPU 名称
     */
    private String name;

    /**
     * 驱动版本文案，如 "Driver 550.54"
     */
    private String driverVersion;

    /**
     * 显存总量文案，如 "8 GiB VRAM"
     */
    private String totalMemory;

    /**
     * GPU 数量文案，如 "1 GPU"
     */
    private String gpuCount;
}
