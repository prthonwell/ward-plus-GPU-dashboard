package org.bsoftware.ward.components;

import org.bsoftware.ward.Ward;
import org.ini4j.Ini;
import org.springframework.stereotype.Component;
import java.io.File;
import java.io.IOException;

/**
 * 通用工具组件：封装 setup.ini 读取（端口、主题、服务器名等配置项）
 *
 * @author Rudolf Barbu
 * @version 1.0.2
 */
@Component
public class UtilitiesComponent
{
    /**
     * 按 section/option 读取 ini 中的字符串值；文件不存在返回 null
     *
     * @param file ini file
     * @param sectionName section in ini filr
     * @param optionName option in section
     * @return String wth parsed data
     * @throws IOException if file does not exists
     */
    @SuppressWarnings(value = "MismatchedQueryAndUpdateOfCollection")
    public String getFromIniFile(File file, String sectionName, String optionName) throws IOException
    {
        if (file.exists())
        {
            Ini ini = new Ini(file);

            return ini.get(sectionName, optionName, String.class);
        }

        return null;
    }

    /**
     * 快捷读取当前主题：light 或 dark
     *
     * @return String wth theme name
     * @throws IOException if file does not exists
     */
    public String getThemeName() throws IOException
    {
        return getFromIniFile(new File(Ward.SETUP_FILE_PATH), "setup", "theme");
    }
}