package org.bsoftware.ward.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.bsoftware.ward.Ward;
import org.bsoftware.ward.dto.UsageDto;
import org.bsoftware.ward.dto.UsageHistoryDto;
import org.bsoftware.ward.dto.UsageHistoryPointDto;
import org.bsoftware.ward.exceptions.ApplicationNotSetUpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 使用率历史：每 8 秒采样追加到项目目录 usage-history.jsonl，
 * 并提供按 10m/1h/3h 读取；清理策略为只保留最近 3 小时（覆盖最大查询窗口）。
 */
@Service
public class UsageHistoryService
{
    private static final Logger log = LoggerFactory.getLogger(UsageHistoryService.class);

    /** 与前端最大区间一致：只保留 3 小时 */
    private static final long RETENTION_MS = 3L * 60L * 60L * 1000L;

    /** 每追加多少条做一次压缩清理，避免每次写盘都重写整文件 */
    private static final int COMPACT_EVERY_APPENDS = 40;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Object fileLock = new Object();
    private final AtomicInteger appendsSinceCompact = new AtomicInteger(0);
    private final Path historyPath = Paths.get(Ward.USAGE_HISTORY_FILE_PATH);

    @Autowired
    private UsageService usageService;

    @PostConstruct
    public void init()
    {
        // 启动时先压一次，丢掉过期数据
        synchronized (fileLock)
        {
            compactLocked();
        }
    }

    /**
     * 固定频率采样；未完成 setup 时跳过。
     * initialDelay 避开启动瞬间的端口切换/重启窗口。
     */
    @Scheduled(fixedDelay = 8000L, initialDelay = 5000L)
    public void sampleAndAppend()
    {
        if (Ward.isFirstLaunch())
        {
            return;
        }

        try
        {
            UsageDto usage = usageService.collectUsage();
            UsageHistoryPointDto point = toPoint(usage, System.currentTimeMillis());
            append(point);
        }
        catch (Exception exception)
        {
            log.warn("Failed to sample usage history: {}", exception.getMessage());
        }
    }

    /**
     * 按区间读取历史；range 支持 10m / 1h / 3h（大小写不敏感）
     */
    public UsageHistoryDto getHistory(String range) throws ApplicationNotSetUpException, IOException
    {
        if (Ward.isFirstLaunch())
        {
            throw new ApplicationNotSetUpException();
        }

        String normalized = normalizeRange(range);
        long windowMs = rangeToMillis(normalized);
        long cutoff = System.currentTimeMillis() - windowMs;

        UsageHistoryDto dto = new UsageHistoryDto();
        dto.setRange(normalized);
        dto.setPoints(readPointsSince(cutoff));
        return dto;
    }

    private void append(UsageHistoryPointDto point) throws IOException
    {
        synchronized (fileLock)
        {
            String line = objectMapper.writeValueAsString(point);
            Files.write(
                    historyPath,
                    (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND
            );

            if (appendsSinceCompact.incrementAndGet() >= COMPACT_EVERY_APPENDS)
            {
                compactLocked();
                appendsSinceCompact.set(0);
            }
        }
    }

    private List<UsageHistoryPointDto> readPointsSince(long cutoff) throws IOException
    {
        synchronized (fileLock)
        {
            List<UsageHistoryPointDto> points = new ArrayList<>();
            if (!Files.exists(historyPath))
            {
                return points;
            }

            try (BufferedReader reader = Files.newBufferedReader(historyPath, StandardCharsets.UTF_8))
            {
                String line;
                while ((line = reader.readLine()) != null)
                {
                    if (line.trim().isEmpty())
                    {
                        continue;
                    }
                    try
                    {
                        UsageHistoryPointDto point = objectMapper.readValue(line, UsageHistoryPointDto.class);
                        if (point.getTimestamp() >= cutoff)
                        {
                            points.add(point);
                        }
                    }
                    catch (IOException ignored)
                    {
                        // 跳过损坏行
                    }
                }
            }

            return points;
        }
    }

    /**
     * 清理逻辑：只保留 [now - 3h, now] 内的行，原子替换文件。
     * 理由：前端最大看 3 小时；更早的数据对当前 UI 无用，可控制 jsonl 体积
     * （8 秒一点 × 3 小时 ≈ 1350 行，通常 &lt; 200KB）。
     */
    private void compactLocked()
    {
        try
        {
            if (!Files.exists(historyPath))
            {
                return;
            }

            long cutoff = System.currentTimeMillis() - RETENTION_MS;
            List<String> kept = new ArrayList<>();

            try (BufferedReader reader = Files.newBufferedReader(historyPath, StandardCharsets.UTF_8))
            {
                String line;
                while ((line = reader.readLine()) != null)
                {
                    if (line.trim().isEmpty())
                    {
                        continue;
                    }
                    try
                    {
                        UsageHistoryPointDto point = objectMapper.readValue(line, UsageHistoryPointDto.class);
                        if (point.getTimestamp() >= cutoff)
                        {
                            kept.add(objectMapper.writeValueAsString(point));
                        }
                    }
                    catch (IOException ignored)
                    {
                        // 丢弃坏行
                    }
                }
            }

            Path tempPath = Paths.get(Ward.USAGE_HISTORY_FILE_PATH + ".tmp");
            try (BufferedWriter writer = Files.newBufferedWriter(tempPath, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE))
            {
                for (String line : kept)
                {
                    writer.write(line);
                    writer.newLine();
                }
            }

            try
            {
                Files.move(tempPath, historyPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
            catch (IOException atomicFailed)
            {
                Files.move(tempPath, historyPath, StandardCopyOption.REPLACE_EXISTING);
            }
            log.debug("Compacted usage history, kept {} points", kept.size());
        }
        catch (IOException exception)
        {
            log.warn("Failed to compact usage history: {}", exception.getMessage());
        }
    }

    private static UsageHistoryPointDto toPoint(UsageDto usage, long timestamp)
    {
        UsageHistoryPointDto point = new UsageHistoryPointDto();
        point.setTimestamp(timestamp);
        point.setProcessor(usage.getProcessor());
        point.setRam(usage.getRam());
        point.setStorage(usage.getStorage());
        point.setGpu(usage.getGpu());
        point.setVram(usage.getVram());
        return point;
    }

    private static String normalizeRange(String range)
    {
        if (range == null || range.trim().isEmpty())
        {
            return "10m";
        }

        String value = range.trim().toLowerCase(Locale.ROOT);
        switch (value)
        {
            case "10m":
            case "1h":
            case "3h":
                return value;
            default:
                return "10m";
        }
    }

    private static long rangeToMillis(String range)
    {
        switch (range)
        {
            case "1h":
                return 60L * 60L * 1000L;
            case "3h":
                return 3L * 60L * 60L * 1000L;
            case "10m":
            default:
                return 10L * 60L * 1000L;
        }
    }
}
