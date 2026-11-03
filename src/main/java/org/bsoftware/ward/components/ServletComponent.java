package org.bsoftware.ward.components;

import org.bsoftware.ward.Ward;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.stereotype.Component;
import java.io.File;
import java.io.IOException;

/**
 * Tomcat 端口定制器：Spring Boot 创建嵌入式容器时回调。
 * 首次启动固定 4000；完成 setup / restart 后从 setup.ini 读取用户配置端口。
 *
 * @author Rudolf Barbu
 * @version 1.0.3
 */
@Component
public class ServletComponent implements WebServerFactoryCustomizer<TomcatServletWebServerFactory>
{
    /**
     * 读取 setup.ini
     */
    @Autowired
    private UtilitiesComponent utilitiesComponent;

    /**
     * 按当前启动阶段绑定监听端口
     *
     * @param tomcatServletWebServerFactory servlet factory
     */
    @Override
    public void customize(TomcatServletWebServerFactory tomcatServletWebServerFactory)
    {
        if (!Ward.isFirstLaunch())
        {
            try
            {
                // 正式运行：使用用户在 setup 页指定的端口
                File file = new File(Ward.SETUP_FILE_PATH);
                tomcatServletWebServerFactory.setPort(Integer.parseInt(utilitiesComponent.getFromIniFile(file, "setup", "port")));
            }
            catch (IOException exception)
            {
                exception.printStackTrace();
            }
        }
        else
        {
            // 初始化向导阶段：固定使用 4000
            tomcatServletWebServerFactory.setPort(Ward.INITIAL_PORT);
        }
    }
}