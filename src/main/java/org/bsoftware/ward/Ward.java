package org.bsoftware.ward;

import lombok.Getter;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.io.File;

/**
 * Ward 应用入口（Spring Boot）。
 * <p>
 * 启动逻辑：
 * 1. 首次以默认端口 4000 启动，并标记 isFirstLaunch = true；
 * 2. 若工作目录已存在 setup.ini（已完成初始化），则调用 restart() 用配置端口重新拉起；
 * 3. 用户在 setup 页提交配置后，SetupService 也会调用 restart() 切换到正式运行态。
 *
 * @author Rudolf Barbu
 * @version 1.0.4
 */
@EnableScheduling
@SpringBootApplication
public class Ward extends SpringBootServletInitializer
{
    /**
     * 应用配置文件名（工作目录下的 setup.ini，保存 serverName / theme / port）
     */
    public static final String SETUP_FILE_PATH = "setup.ini";

    /**
     * 使用率历史 jsonl（工作目录，8 秒追加一条，最多保留约 3 小时）
     */
    public static final String USAGE_HISTORY_FILE_PATH = "usage-history.jsonl";

    /**
     * 未完成 setup 时的默认监听端口
     */
    public static final int INITIAL_PORT = 4000;

    /**
     * true：尚未完成初始化（应展示 setup 页，API 不可用）；
     * false：已读取 setup.ini，进入正式监控态
     */
    @Getter
    private static boolean isFirstLaunch;

    /**
     * 当前 Spring 应用上下文；restart() 时会先 close 再重新 run
     */
    private static ConfigurableApplicationContext configurableApplicationContext;

    /**
     * 应用入口：先冷启动，若已有配置文件则立刻重启进入正式模式
     *
     * @param args Spring Boot application arguments
     */
    public static void main(String[] args)
    {
        isFirstLaunch = true;
        configurableApplicationContext = SpringApplication.run(Ward.class, args);

        // 已存在 setup.ini → 说明不是真正的“首次安装”，需要按配置端口重启
        File setupFile = new File(Ward.SETUP_FILE_PATH);
        if (setupFile.exists())
        {
            restart();
        }
    }

    /**
     * 关闭当前上下文并在新线程中重新启动。
     * 目的：让 ServletComponent 能读到 setup.ini 中的自定义端口。
     */
    public static void restart()
    {
        isFirstLaunch = false;
        ApplicationArguments args = configurableApplicationContext.getBean(ApplicationArguments.class);

        Thread thread = new Thread(() ->
        {
            configurableApplicationContext.close();
            configurableApplicationContext = SpringApplication.run(Ward.class, args.getSourceArgs());
        });

        thread.setDaemon(false);
        thread.start();
    }
}