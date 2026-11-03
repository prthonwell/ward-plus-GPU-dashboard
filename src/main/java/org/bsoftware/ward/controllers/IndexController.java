package org.bsoftware.ward.controllers;

import org.bsoftware.ward.services.IndexService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 页面入口控制器：GET / → setup.html 或 index.html（由 IndexService 按 isFirstLaunch 决定）
 *
 * @author Rudolf Barbu
 * @version 1.0.2
 */
@Controller
@RequestMapping(value = "/")
public class IndexController
{
    /**
     * 首页业务：决定模板并填充硬件信息 / 主题
     */
    @Autowired
    private IndexService indexService;

    /**
     * 打开根路径时渲染仪表盘或初始化向导
     *
     * @param model used for providing values in to html template
     * @return String name of html template with values from model param
     */
    @GetMapping
    public String getIndex(Model model) throws Exception
    {
        return indexService.getIndex(model);
    }
}