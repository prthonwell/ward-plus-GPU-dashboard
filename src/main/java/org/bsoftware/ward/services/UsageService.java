package org.bsoftware.ward.services;

import org.bsoftware.ward.Ward;
import org.bsoftware.ward.components.NvmlComponent;
import org.bsoftware.ward.dto.UsageDto;
import org.bsoftware.ward.exceptions.ApplicationNotSetUpException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.software.os.FileSystem;
import oshi.software.os.OSFileStore;
import oshi.util.Util;
import java.util.Arrays;

/**
 * 动态资源占用采集服务：返回 CPU / RAM / 磁盘 / GPU / 显存 使用率（0–100）。
 * 历史采样由 UsageHistoryService 每 8 秒调用 collectUsage() 写入 jsonl；
 * getProcessor() 内部 sleep 1s 用于两次 tick 差分。
 *
 * @author Rudolf Barbu
 * @version 1.0.3
 */
@Service
public class UsageService
{
    /**
     * OSHI 系统信息入口
     */
    @Autowired
    private SystemInfo systemInfo;

    /**
     * NVIDIA NVML：GPU / 显存实时占用
     */
    @Autowired
    private NvmlComponent nvmlComponent;

    /**
     * 通过两次 tick 采样差计算 CPU 使用率（中间休眠 1 秒）
     *
     * @return int that display processor usage
     */
    private int getProcessor()
    {
        CentralProcessor centralProcessor = systemInfo.getHardware().getProcessor();

        // 第一次采样
        long[] prevTicksArray = centralProcessor.getSystemCpuLoadTicks();
        long prevTotalTicks = Arrays.stream(prevTicksArray).sum();
        long prevIdleTicks = prevTicksArray[CentralProcessor.TickType.IDLE.getIndex()];

        Util.sleep(1000);

        // 第二次采样，用 idle 占比反推 busy 占比
        long[] currTicksArray = centralProcessor.getSystemCpuLoadTicks();
        long currTotalTicks = Arrays.stream(currTicksArray).sum();
        long currIdleTicks = currTicksArray[CentralProcessor.TickType.IDLE.getIndex()];

        return (int) Math.round((1 - ((double) (currIdleTicks - prevIdleTicks)) / ((double) (currTotalTicks - prevTotalTicks))) * 100);
    }

    /**
     * Gets ram usage
     *
     * @return int that display ram usage
     */
    private int getRam()
    {
        GlobalMemory globalMemory = systemInfo.getHardware().getMemory();

        long totalMemory = globalMemory.getTotal();
        long availableMemory = globalMemory.getAvailable();

        return (int) Math.round(100 - (((double) availableMemory / totalMemory) * 100));
    }

    /**
     * Gets storage usage
     *
     * @return int that display storage usage
     */
    private int getStorage()
    {
        FileSystem fileSystem = systemInfo.getOperatingSystem().getFileSystem();

        long totalStorage = fileSystem.getFileStores().stream().mapToLong(OSFileStore::getTotalSpace).sum();
        long freeStorage = fileSystem.getFileStores().stream().mapToLong(OSFileStore::getFreeSpace).sum();

        return (int) Math.round(((double) (totalStorage - freeStorage) / totalStorage) * 100);
    }

    /**
     * NVML GPU 计算利用率；无卡返回 0
     */
    private int getGpu()
    {
        return nvmlComponent.getGpuUtilization();
    }

    /**
     * NVML 显存占用率；无卡返回 0
     */
    private int getVram()
    {
        return nvmlComponent.getMemoryUtilization();
    }

    /**
     * 采集当前占用率（供 API 与历史采样共用）；不做 firstLaunch 校验
     */
    public UsageDto collectUsage()
    {
        UsageDto usageDto = new UsageDto();
        usageDto.setProcessor(getProcessor());
        usageDto.setRam(getRam());
        usageDto.setStorage(getStorage());
        usageDto.setGpu(getGpu());
        usageDto.setVram(getVram());
        return usageDto;
    }

    /**
     * Used to deliver dto to corresponding controller
     *
     * @return ResponseEntityWrapperAsset filled with usageDto
     */
    public UsageDto getUsage() throws ApplicationNotSetUpException
    {
        if (!Ward.isFirstLaunch())
        {
            return collectUsage();
        }
        else
        {
            throw new ApplicationNotSetUpException();
        }
    }
}