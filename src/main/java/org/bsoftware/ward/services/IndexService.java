package org.bsoftware.ward.services;

import org.bsoftware.ward.Ward;
import org.bsoftware.ward.components.UtilitiesComponent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;

/**
 * 首页路由服务：根据是否完成初始化，决定返回 setup 页还是监控仪表盘。
 *
 * @author Rudolf Barbu
 * @version 1.0.1
 */
@Service
public class IndexService
{
    /**
     * 采集静态硬件/系统信息，注入到 index.html 模板
     */
    @Autowired
    private InfoService infoService;

    /**
     * 读取 setup.ini 中的主题名
     */
    @Autowired
    private UtilitiesComponent utilitiesComponent;

    /**
     * 填充 Model 并返回 Thymeleaf 模板名。
     * 未 setup → "setup"；已 setup → "index"（附带 info + theme）
     *
     * @param model strings container
     * @return template name
     */
    public String getIndex(Model model) throws Exception
    {
        // 首次启动尚未写入 setup.ini：强制进入初始化向导
        if (Ward.isFirstLaunch())
        {
            return "setup";
        }

        model.addAttribute("info", infoService.getInfo());
        model.addAttribute("theme", utilitiesComponent.getThemeName());

        return "index";
    }
}