package org.bsoftware.ward.components;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Arrays;

/**
 * NVIDIA NVML 封装：通过 JNA 读取 GPU 利用率与显存。
 * 无 NVIDIA 驱动 / 无 GPU / 库加载失败时 available=false，调用方应降级为 0 或占位文案。
 */
@Component
public class NvmlComponent
{
    private static final Logger log = LoggerFactory.getLogger(NvmlComponent.class);

    private static final int NVML_SUCCESS = 0;
    private static final int NAME_BUFFER_SIZE = 96;
    private static final int DRIVER_BUFFER_SIZE = 80;

    @Getter
    private boolean available;

    private NvmlLibrary library;

    public interface NvmlLibrary extends Library
    {
        int nvmlInit_v2();

        int nvmlShutdown();

        int nvmlDeviceGetCount_v2(IntByReference deviceCount);

        int nvmlDeviceGetHandleByIndex_v2(int index, PointerByReference device);

        int nvmlDeviceGetName(Pointer device, byte[] name, int length);

        int nvmlDeviceGetMemoryInfo(Pointer device, NvmlMemory memory);

        int nvmlDeviceGetUtilizationRates(Pointer device, NvmlUtilization utilization);

        int nvmlSystemGetDriverVersion(byte[] version, int length);
    }

    /**
     * NVML 显存结构：total / free / used，单位字节
     */
    @Structure.FieldOrder({"total", "free", "used"})
    public static class NvmlMemory extends Structure
    {
        public long total;
        public long free;
        public long used;
    }

    /**
     * NVML 利用率结构：gpu / memory，单位百分比
     */
    @Structure.FieldOrder({"gpu", "memory"})
    public static class NvmlUtilization extends Structure
    {
        public int gpu;
        public int memory;
    }

    @PostConstruct
    public void init()
    {
        // 会依次尝试多个库名（如 nvidia-ml、libnvidia-ml.so.1），
        // 失败日志只在全部尝试完后打一次，避免每个候选都打 "no GPU"
        for (String libraryName : resolveLibraryNames())
        {
            try
            {
                NvmlLibrary loaded = Native.load(libraryName, NvmlLibrary.class);
                if (loaded.nvmlInit_v2() == NVML_SUCCESS)
                {
                    library = loaded;
                    available = true;
                    return;
                }
                loaded.nvmlShutdown();
            }
            catch (Throwable ignored)
            {
                library = null;
            }
        }

        available = false;
        library = null;
        log.info("no GPU");
    }

    @PreDestroy
    public void destroy()
    {
        if (available && library != null)
        {
            library.nvmlShutdown();
            available = false;
        }
    }

    /**
     * @return GPU 名称；不可用时返回 null
     */
    public String getName()
    {
        Pointer device = firstDevice();
        if (device == null)
        {
            return null;
        }

        byte[] buffer = new byte[NAME_BUFFER_SIZE];
        if (library.nvmlDeviceGetName(device, buffer, buffer.length) != NVML_SUCCESS)
        {
            return null;
        }

        return nativeString(buffer);
    }

    /**
     * @return 驱动版本；不可用时返回 null
     */
    public String getDriverVersion()
    {
        if (!available)
        {
            return null;
        }

        byte[] buffer = new byte[DRIVER_BUFFER_SIZE];
        if (library.nvmlSystemGetDriverVersion(buffer, buffer.length) != NVML_SUCCESS)
        {
            return null;
        }

        return nativeString(buffer);
    }

    /**
     * @return GPU 数量；不可用时返回 0
     */
    public int getDeviceCount()
    {
        if (!available)
        {
            return 0;
        }

        IntByReference count = new IntByReference();
        if (library.nvmlDeviceGetCount_v2(count) != NVML_SUCCESS)
        {
            return 0;
        }

        return count.getValue();
    }

    /**
     * @return 显存总量（字节）；不可用时返回 0
     */
    public long getTotalMemoryBytes()
    {
        NvmlMemory memory = readMemory();
        return memory == null ? 0L : memory.total;
    }

    /**
     * @return GPU 计算利用率 0–100；不可用时返回 0
     */
    public int getGpuUtilization()
    {
        Pointer device = firstDevice();
        if (device == null)
        {
            return 0;
        }

        NvmlUtilization utilization = new NvmlUtilization();
        if (library.nvmlDeviceGetUtilizationRates(device, utilization) != NVML_SUCCESS)
        {
            return 0;
        }

        return utilization.gpu;
    }

    /**
     * @return 显存占用率 0–100；不可用时返回 0
     */
    public int getMemoryUtilization()
    {
        NvmlMemory memory = readMemory();
        if (memory == null || memory.total == 0L)
        {
            return 0;
        }

        return (int) Math.round(((double) memory.used / memory.total) * 100);
    }

    private NvmlMemory readMemory()
    {
        Pointer device = firstDevice();
        if (device == null)
        {
            return null;
        }

        NvmlMemory memory = new NvmlMemory();
        if (library.nvmlDeviceGetMemoryInfo(device, memory) != NVML_SUCCESS)
        {
            return null;
        }

        return memory;
    }

    private Pointer firstDevice()
    {
        if (!available || getDeviceCount() < 1)
        {
            return null;
        }

        PointerByReference device = new PointerByReference();
        if (library.nvmlDeviceGetHandleByIndex_v2(0, device) != NVML_SUCCESS)
        {
            return null;
        }

        return device.getValue();
    }

    /**
     * 按平台尝试多个库名。
     * macOS 通常无 NVIDIA/NVML，直接返回空数组，避免无意义的 Native.load。
     */
    private static String[] resolveLibraryNames()
    {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac") || os.contains("darwin"))
        {
            return new String[0];
        }
        if (os.contains("win"))
        {
            return new String[] {"nvml"};
        }

        // Linux：常见为 libnvidia-ml.so / libnvidia-ml.so.1
        return new String[] {"nvidia-ml", "libnvidia-ml.so.1"};
    }

    private static String nativeString(byte[] buffer)
    {
        int length = 0;
        while (length < buffer.length && buffer[length] != 0)
        {
            length++;
        }

        return new String(Arrays.copyOf(buffer, length));
    }
}
