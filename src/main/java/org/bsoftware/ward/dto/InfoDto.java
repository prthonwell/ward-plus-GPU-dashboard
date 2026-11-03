package org.bsoftware.ward.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * /api/info 响应聚合体：嵌套 processor / machine / storage / gpu / uptime / setup / project
 *
 * @author Rudolf Barbu
 * @version 1.0.1
 */
@Getter
@Setter
public class InfoDto
{
    /**
     *  Processor info dto field
     */
    private ProcessorDto processor;

    /**
     *  machine info dto field
     */
    private MachineDto machine;

    /**
     *  Storage info dto field
     */
    private StorageDto storage;

    /**
     *  GPU info dto field（NVML）
     */
    private GpuDto gpu;

    /**
     *  Uptime info dto field
     */
    private UptimeDto uptime;

    /**
     *  Setup info dto field
     */
    private SetupDto setup;

    /**
     *  Maven info dto field
     */
    private ProjectDto project;
}