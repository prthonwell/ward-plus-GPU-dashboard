"use strict";

/**
 * 用 Vanta FOG 初始化动态雾效背景，颜色随 light/dark 主题切换
 */
function backgroundInitialization()
{
    background = VANTA.FOG({el: "#background", blurFactor: 0.40, zoom: 1.50});

    if (html.getAttribute("theme") == "light")
    {
        background.setOptions
        ({
            highlightColor: 0xCAC7E8,
            midtoneColor: 0xBBB7ED,
            lowlightColor: 0xE4E3EF,
            baseColor: 0xE4E3EF
        });
    }
    else
    {
        background.setOptions
        ({
            highlightColor: 0x797979,
            midtoneColor: 0xFFFFFF,
            lowlightColor: 0xBCBCBC,
            baseColor: 0xBCBCBC
        });
    }
}