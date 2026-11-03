package org.bsoftware.ward.services;

import org.bsoftware.ward.Ward;
import org.bsoftware.ward.dto.ResponseDto;
import org.bsoftware.ward.dto.SetupDto;
import org.ini4j.Ini;
import org.springframework.stereotype.Service;
import java.io.File;
import java.io.IOException;

/**
 * 初始化配置服务：将用户在 setup 页提交的 serverName / theme / port 写入 setup.ini，
 * 然后触发 Ward.restart() 使 ServletComponent 绑定新端口并进入正式运行态。
 *
 * @author Rudolf Barbu
 * @version 1.0.2
 */
@Service
public class SetupService
{
    /**
     * 向 ini 写入单个键值（文件不存在时会创建）
     *
     * @param file ini file
     * @param sectionName section in ini file
     * @param optionName option in section
     * @throws IOException if file does not exists
     */
    @SuppressWarnings(value = {"ResultOfMethodCallIgnored", "SameParameterValue"})
    private void putInIniFile(File file, String sectionName, String optionName, String value) throws IOException
    {
        file.createNewFile();
        Ini ini = new Ini(file);

        ini.put(sectionName, optionName, value);

        ini.store();
    }

    /**
     * 仅在首次启动时落盘配置并重启；非首次调用只返回成功消息。
     *
     * @param setupDto user settings data
     * @return ResponseEntityWrapperAsset filled with ResponseDto
     * @throws Exception IoException if file is fot found, and cant be created
     */
    public ResponseDto postSetup(SetupDto setupDto) throws Exception
    {
        if (Ward.isFirstLaunch())
        {
            File file = new File(Ward.SETUP_FILE_PATH);

            // setup.ini 结构示例：
            // [setup]
            // serverName=MyServer
            // theme=light
            // port=8080
            putInIniFile(file, "setup", "serverName", setupDto.getServerName());
            putInIniFile(file, "setup", "theme", setupDto.getTheme());
            putInIniFile(file, "setup", "port", setupDto.getPort());

            // 重启后 isFirstLaunch=false，Tomcat 使用用户指定端口
            Ward.restart();
        }

        return new ResponseDto("Settings saved correctly");
    }
}