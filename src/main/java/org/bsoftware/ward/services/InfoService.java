package org.bsoftware.ward.services;

import org.bsoftware.ward.Ward;
import org.bsoftware.ward.components.NvmlComponent;
import org.bsoftware.ward.components.UtilitiesComponent;
import org.bsoftware.ward.dto.*;
import org.bsoftware.ward.exceptions.ApplicationNotSetUpException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.*;
import oshi.software.os.OperatingSystem;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * 静态/半静态机器信息采集服务（基于 OSHI + NVML）。
 * 汇总 CPU、内存、磁盘、GPU、运行时长、服务器名、项目版本等，供首页模板渲染与 /api/info 轮询刷新。
 *
 * @author Rudolf Barbu
 * @version 1.0.2
 */
@Service
public class InfoService
{
    /**
     * OSHI 系统信息入口，跨平台读取硬件与操作系统数据
     */
    @Autowired
    private SystemInfo systemInfo;

    /**
     * 从 setup.ini 读取服务器显示名等配置项
     */
    @Autowired
    private UtilitiesComponent utilitiesComponent;

    /**
     * NVIDIA NVML：GPU 名称 / 驱动 / 显存总量
     */
    @Autowired
    private NvmlComponent nvmlComponent;

    /**
     * CPU 静态信息缓存：Apple Silicon 上反复读频率会刷 sysctl ERROR，且值几乎不变
     */
    private volatile ProcessorDto cachedProcessor;

    /**
     * GPU 静态信息缓存
     */
    private volatile GpuDto cachedGpu;

    /**
     * 项目版本缓存
     */
    private volatile ProjectDto cachedProject;

    /**
     * Converts frequency to most readable format
     *
     * @param hertzArray raw frequency array values in hertz for each logical processor
     * @return String with formatted frequency and postfix
     */
    private String getConvertedFrequency(long[] hertzArray)
    {
        long totalFrequency = Arrays.stream(hertzArray).sum();
        long hertz = totalFrequency / hertzArray.length;

        if ((hertz / 1E+6) > 999)
        {
            return (Math.round((hertz / 1E+9) * 10.0) / 10.0) + " GHz";
        }
        else
        {
            return Math.round(hertz / 1E+6) + " MHz";
        }
    }

    /**
     * Converts capacity to most readable format
     *
     * @param bits raw capacity value in bits
     * @return String with formatted capacity and postfix
     */
    private String getConvertedCapacity(long bits)
    {
        if ((bits / 1.049E+6) > 999)
        {
            if ((bits / 1.074E+9) > 999)
            {
                return (Math.round((bits / 1.1E+12) * 10.0) / 10.0) + " TiB";
            }
            else
            {
                return Math.round(bits / 1.074E+9) + " GiB";
            }
        }
        else
        {
            return Math.round(bits / 1.049E+6) + " MiB";
        }
    }

    /**
     * Gets processor information（结果缓存，避免每秒触发 Apple Silicon 上无效的 sysctl）
     *
     * @return ProcessorDto with filled fields
     */
    private ProcessorDto getProcessor()
    {
        if (cachedProcessor != null)
        {
            return cachedProcessor;
        }

        ProcessorDto processorDto = new ProcessorDto();

        CentralProcessor centralProcessor = systemInfo.getHardware().getProcessor();

        String name = centralProcessor.getProcessorIdentifier().getName();
        if (name.contains("@"))
        {
            name = name.substring(0, name.indexOf('@') - 1);
        }
        processorDto.setName(name.trim());

        int coreCount = centralProcessor.getLogicalProcessorCount();
        processorDto.setCoreCount(coreCount + ((coreCount > 1) ? " Cores" : " Core"));
        // Apple Silicon 常无 hw.cpufrequency，可能得到 0 MHz；只读一次即可
        processorDto.setClockSpeed(getConvertedFrequency(centralProcessor.getCurrentFreq()));

        String BitDepthPrefix = centralProcessor.getProcessorIdentifier().isCpu64bit() ? "64" : "32";
        processorDto.setBitDepth(BitDepthPrefix + "-bit");

        cachedProcessor = processorDto;
        return processorDto;
    }

    /**
     * Gets machine information
     *
     * @return MachineDto with filled fields
     */
    private MachineDto getMachine()
    {
        MachineDto machineDto = new MachineDto();

        OperatingSystem operatingSystem = systemInfo.getOperatingSystem();
        OperatingSystem.OSVersionInfo osVersionInfo = systemInfo.getOperatingSystem().getVersionInfo();
        GlobalMemory globalMemory = systemInfo.getHardware().getMemory();

        machineDto.setOperatingSystem(operatingSystem.getFamily() + " " + osVersionInfo.getVersion() + ", " + osVersionInfo.getCodeName());
        machineDto.setTotalRam(getConvertedCapacity(globalMemory.getTotal()) + " Ram");

        Optional<PhysicalMemory> physicalMemoryOptional = globalMemory.getPhysicalMemory().stream().findFirst();
        if (physicalMemoryOptional.isPresent())
        {
            machineDto.setRamTypeOrOSBitDepth(physicalMemoryOptional.get().getMemoryType());
        }
        else
        {
            machineDto.setRamTypeOrOSBitDepth(operatingSystem.getBitness() + "-bit");
        }

        int processCount = operatingSystem.getProcessCount();
        machineDto.setProcCount(processCount + ((processCount > 1) ? " Procs" : " Proc"));

        return machineDto;
    }

    /**
     * Gets storage information
     *
     * @return StorageDto with filled fields
     */
    private StorageDto getStorage()
    {
        StorageDto storageDto = new StorageDto();

        List<HWDiskStore> hwDiskStores = systemInfo.getHardware().getDiskStores();
        GlobalMemory globalMemory = systemInfo.getHardware().getMemory();

        Optional<HWDiskStore> hwDiskStoreOptional = hwDiskStores.stream().findFirst();
        if (hwDiskStoreOptional.isPresent())
        {
            String mainStorage = hwDiskStoreOptional.get().getModel();

            if (mainStorage.contains("(Standard disk drives)"))
            {
                mainStorage = mainStorage.substring(0, mainStorage.indexOf("(Standard disk drives)") - 1);
            }

            storageDto.setMainStorage(mainStorage.trim());
        }
        else
        {
            storageDto.setMainStorage("Undefined");
        }

        long total = hwDiskStores.stream().mapToLong(HWDiskStore::getSize).sum();
        storageDto.setTotal(getConvertedCapacity(total) + " Total");

        int diskCount = hwDiskStores.size();
        storageDto.setDiskCount(diskCount + ((diskCount > 1) ? " Disks" : " Disk"));

        storageDto.setSwapAmount(getConvertedCapacity(globalMemory.getVirtualMemory().getSwapTotal()) + " Swap");

        return storageDto;
    }

    /**
     * Gets uptime information
     *
     * @return UptimeDto with filled fields
     */
    @SuppressWarnings(value = "IntegerDivisionInFloatingPointContext")
    private UptimeDto getUptime()
    {
        UptimeDto uptimeDto = new UptimeDto();

        long uptimeInSeconds = systemInfo.getOperatingSystem().getSystemUptime();

        uptimeDto.setDays(String.format("%02d", (int) Math.floor(uptimeInSeconds / 86400)));
        uptimeDto.setHours(String.format("%02d", (int) Math.floor((uptimeInSeconds % 86400) / 3600)));
        uptimeDto.setMinutes(String.format("%02d", (int) Math.floor((uptimeInSeconds / 60) % 60)));
        uptimeDto.setSeconds(String.format("%02d", (int) Math.floor(uptimeInSeconds % 60)));

        return uptimeDto;
    }

    /**
     * Gets server name information
     *
     * @return SetupDto with filled field
     * @throws IOException if file does not exists
     */
    private SetupDto getSetup() throws IOException
    {
        SetupDto setupDto = new SetupDto();
        File file = new File(Ward.SETUP_FILE_PATH);

        setupDto.setServerName(utilitiesComponent.getFromIniFile(file, "setup", "serverName"));

        return setupDto;
    }

    /**
     * Gets project version information
     *
     * @return MavenDto with filled field
     * @throws IOException if file does not exists
     */
    private ProjectDto getProject() throws IOException
    {
        if (cachedProject != null)
        {
            return cachedProject;
        }

        ProjectDto projectDto = new ProjectDto();
        Properties properties = new Properties();
        InputStream inputStream = getClass().getResourceAsStream("/META-INF/maven/org.b-software/ward/pom.properties");

        if (inputStream != null)
        {
            properties.load(inputStream);
            String version = properties.getProperty("version");

            projectDto.setVersion("Ward: v" + version);
        }
        else
        {
            projectDto.setVersion("Developer mode");
        }

        cachedProject = projectDto;
        return projectDto;
    }

    /**
     * 通过 NVML 读取第一张 NVIDIA GPU 的静态信息；不可用时 available=false（结果缓存）
     */
    private GpuDto getGpu()
    {
        if (cachedGpu != null)
        {
            return cachedGpu;
        }

        GpuDto gpuDto = new GpuDto();

        if (!nvmlComponent.isAvailable() || nvmlComponent.getDeviceCount() < 1)
        {
            gpuDto.setAvailable(false);
            gpuDto.setName("No NVIDIA GPU");
            gpuDto.setDriverVersion("N/A");
            gpuDto.setTotalMemory("0 MiB VRAM");
            gpuDto.setGpuCount("0 GPU");
            cachedGpu = gpuDto;
            return gpuDto;
        }

        gpuDto.setAvailable(true);

        String name = nvmlComponent.getName();
        gpuDto.setName(name != null ? name : "NVIDIA GPU");

        String driverVersion = nvmlComponent.getDriverVersion();
        gpuDto.setDriverVersion(driverVersion != null ? ("Driver " + driverVersion) : "Driver N/A");

        // getConvertedCapacity 实际按字节换算（与 OSHI memory/disk 用法一致）
        gpuDto.setTotalMemory(getConvertedCapacity(nvmlComponent.getTotalMemoryBytes()) + " VRAM");

        int gpuCount = nvmlComponent.getDeviceCount();
        gpuDto.setGpuCount(gpuCount + ((gpuCount > 1) ? " GPUs" : " GPU"));

        cachedGpu = gpuDto;
        return gpuDto;
    }

    /**
     * 组装完整 InfoDto；未完成 setup 时拒绝访问。
     * 前端每秒轮询 /api/info，主要用于刷新时钟频率、进程数、磁盘总量与 uptime。
     *
     * @return InfoDto filled with server info
     */
    public InfoDto getInfo() throws Exception
    {
        if (!Ward.isFirstLaunch())
        {
            InfoDto infoDto = new InfoDto();

            infoDto.setProcessor(getProcessor());
            infoDto.setMachine(getMachine());
            infoDto.setStorage(getStorage());
            infoDto.setGpu(getGpu());
            infoDto.setUptime(getUptime());
            infoDto.setSetup(getSetup());
            infoDto.setProject(getProject());

            return infoDto;
        }
        else
        {
            throw new ApplicationNotSetUpException();
        }
    }
}