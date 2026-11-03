package org.bsoftware.ward.configurations;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import oshi.SystemInfo;

/**
 * Spring Bean 注册：把非 Spring 组件（OSHI SystemInfo）纳入 IoC，供 InfoService / UsageService 注入。
 *
 * @author Rudolf Barbu
 * @version 1.0.2
 */
@Configuration
public class BeanConfiguration
{
    /**
     * 跨平台硬件/OS 信息采集入口（OSHI）
     *
     * @return SystemInfo object
     */
    @Bean
    public SystemInfo systemInfo()
    {
        return new SystemInfo();
    }
}