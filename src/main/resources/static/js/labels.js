"use strict";

/**
 * Initializes labels span arrays
 */
function labelsInitialization()
{
    processorLabelsArray =
    [
        document.getElementById("processor-hundreds"),
        document.getElementById("processor-tens"),
        document.getElementById("processor-ones")
    ];
    ramLabelsArray =
    [
        document.getElementById("ram-hundreds"),
        document.getElementById("ram-tens"),
        document.getElementById("ram-ones")
    ];
    storageLabelsArray =
    [
        document.getElementById("storage-hundreds"),
        document.getElementById("storage-tens"),
        document.getElementById("storage-ones")
    ];
    gpuLabelsArray =
    [
        document.getElementById("gpu-hundreds"),
        document.getElementById("gpu-tens"),
        document.getElementById("gpu-ones")
    ];
    currentVramUsage = document.getElementById("currentVramUsage");
}

/**
 * 将 usage 百分比拆成百/十/个三位数字，分别写入对应 span，并按是否前导零切换颜色。
 * vram 单独刷新卡片脚注文案（无独立大字号数字）。
 *
 * @param {*} usageData usage value
 */
function labelsTick(usageData)
{
    let usageDataArray = Object.values(usageData);

    for (let i = 0; i < usageDataArray.length; i++)
    {
        switch (i)
        {
            case 0:
            {
                formatLabels(processorLabelsArray, usageDataArray[i]);
                break;
            }
            case 1:
            {
                formatLabels(ramLabelsArray, usageDataArray[i]);
                break;
            }
            case 2:
            {
                formatLabels(storageLabelsArray, usageDataArray[i]);
                break;
            }
            case 3:
            {
                formatLabels(gpuLabelsArray, usageDataArray[i]);
                break;
            }
            case 4:
            {
                if (currentVramUsage)
                {
                    currentVramUsage.innerHTML = usageDataArray[i] + "% VRAM";
                }
                break;
            }
        }
    }
}

/**
 * Distributes values to spans and changes their color
 *
 * @param {*} labelArray array with domObjects
 * @param {*} usageData usage value to distribute
 */
function formatLabels(labelArray, usageData)
{
    let usageDataString = String(usageData);

    switch (usageDataString.length)
    {
        case 1:
        {
            labelArray[0].innerHTML = 0;
            labelArray[0].style.color = (html.getAttribute("theme") == "light") ? "rgba(188, 188, 188, 1)" : "rgba(121, 121, 121, 1)";
            labelArray[1].innerHTML = 0;
            labelArray[1].style.color = (html.getAttribute("theme") == "light") ? "rgba(188, 188, 188, 1)" : "rgba(121, 121, 121, 1)";
            labelArray[2].innerHTML = usageDataString[0];
            labelArray[2].style.color = (html.getAttribute("theme") == "light") ? "rgba(0, 0, 0, 1)" : "rgba(255, 255, 255, 1)";
            break;
        }
        case 2:
        {
            labelArray[0].innerHTML = 0;
            labelArray[0].style.color = (html.getAttribute("theme") == "light") ? "rgba(188, 188, 188, 1)" : "rgba(121, 121, 121, 1)";
            labelArray[1].innerHTML = usageDataString[0];
            labelArray[1].style.color = (html.getAttribute("theme") == "light") ? "rgba(0, 0, 0, 1)" : "rgba(255, 255, 255, 1)";
            labelArray[2].innerHTML = usageDataString[1];
            labelArray[2].style.color = (html.getAttribute("theme") == "light") ? "rgba(0, 0, 0, 1)" : "rgba(255, 255, 255, 1)";
            break;
        }
        default:
        {
            labelArray[0].innerHTML = usageDataString[0];
            labelArray[0].style.color = (html.getAttribute("theme") == "light") ? "rgba(0, 0, 0, 1)" : "rgba(255, 255, 255, 1)";
            labelArray[1].innerHTML = usageDataString[1];
            labelArray[1].style.color = (html.getAttribute("theme") == "light") ? "rgba(0, 0, 0, 1)" : "rgba(255, 255, 255, 1)";
            labelArray[2].innerHTML = usageDataString[2];
            labelArray[2].style.color = (html.getAttribute("theme") == "light") ? "rgba(0, 0, 0, 1)" : "rgba(255, 255, 255, 1)";
        }
    }
}