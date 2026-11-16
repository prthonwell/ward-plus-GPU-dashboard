"use strict";

/**
 * 当前历史查询区间：10m / 1h / 3h
 */
let currentHistoryRange = "10m";

/**
 * 四张图：CPU / RAM / Disk / GPU+VRAM
 */
let cpuChart;
let ramChart;
let diskChart;
let gpuChart;

/**
 * 初始化四张利用率图（显示横纵轴；进入页后由 loadUsageHistory 填数据）
 */
function chartInitialization()
{
    let commonOptions = buildCommonOptions();
    let themeLight = (html.getAttribute("theme") == "light");

    cpuChart = new Chart(document.getElementById("chart-cpu").getContext("2d"),
    {
        type: "line",
        data:
        {
            labels: [],
            datasets: [buildDataset(themeLight ? "rgba(89, 101, 249, 1)" : "rgba(188, 188, 188, 1)",
                                    themeLight ? "rgba(230, 232, 254, 0.3)" : "rgba(121, 121, 121, 0.5)", "CPU")]
        },
        options: commonOptions
    });

    ramChart = new Chart(document.getElementById("chart-ram").getContext("2d"),
    {
        type: "line",
        data:
        {
            labels: [],
            datasets: [buildDataset(themeLight ? "rgba(255, 89, 89, 1)" : "rgba(188, 188, 188, 1)",
                                    themeLight ? "rgba(249, 226, 226, 0.3)" : "rgba(121, 121, 121, 0.5)", "RAM")]
        },
        options: commonOptions
    });

    diskChart = new Chart(document.getElementById("chart-disk").getContext("2d"),
    {
        type: "line",
        data:
        {
            labels: [],
            datasets: [buildDataset(themeLight ? "rgba(8, 193, 141, 1)" : "rgba(188, 188, 188, 1)",
                                    themeLight ? "rgba(212, 242, 225, 0.3)" : "rgba(121, 121, 121, 0.5)", "Disk")]
        },
        options: commonOptions
    });

    gpuChart = new Chart(document.getElementById("chart-gpu").getContext("2d"),
    {
        type: "line",
        data:
        {
            labels: [],
            datasets:
            [
                buildDataset(themeLight ? "rgba(255, 159, 67, 1)" : "rgba(188, 188, 188, 1)",
                             themeLight ? "rgba(255, 236, 210, 0.3)" : "rgba(121, 121, 121, 0.5)", "GPU"),
                buildDataset(themeLight ? "rgba(156, 89, 249, 1)" : "rgba(160, 160, 160, 1)",
                             themeLight ? "rgba(232, 220, 252, 0.3)" : "rgba(121, 121, 121, 0.35)", "VRAM")
            ]
        },
        options: Object.assign({}, commonOptions,
        {
            legend:
            {
                display: true,
                labels:
                {
                    boxWidth: 10,
                    fontSize: 10
                }
            }
        })
    });

    bindChartControls();
}

/**
 * 绑定区间按钮与 Refresh（仅点击时拉历史，不自动轮询图表）
 */
function bindChartControls()
{
    let rangeButtons = document.getElementsByClassName("charts-range-btn");
    for (let i = 0; i < rangeButtons.length; i++)
    {
        rangeButtons[i].addEventListener("click", function(event)
        {
            let button = event.target || event.srcElement;
            currentHistoryRange = button.getAttribute("data-range") || "10m";

            for (let k = 0; k < rangeButtons.length; k++)
            {
                rangeButtons[k].classList.remove("active");
            }
            button.classList.add("active");

            loadUsageHistory();
        });
    }

    let refreshButton = document.getElementById("charts-refresh");
    if (refreshButton)
    {
        refreshButton.addEventListener("click", function()
        {
            loadUsageHistory();
        });
    }
}

/**
 * 请求 /api/usage/history 并重绘四图；顺带用最后一点刷新顶部百分比卡片
 */
function loadUsageHistory()
{
    if (!usageXHR)
    {
        usageXHR = new XMLHttpRequest();
    }

    usageXHR.onreadystatechange = function()
    {
        if ((this.readyState == 4) && (this.status == 200))
        {
            let response = JSON.parse(this.response);
            applyHistoryToCharts(response.points || []);

            let points = response.points || [];
            if (points.length > 0)
            {
                let latest = points[points.length - 1];
                labelsTick(
                {
                    processor: latest.processor,
                    ram: latest.ram,
                    storage: latest.storage,
                    gpu: latest.gpu,
                    vram: latest.vram
                });
            }
        }
    };

    usageXHR.open("GET", "/api/usage/history?range=" + encodeURIComponent(currentHistoryRange));
    usageXHR.send();
}

/**
 * 把历史点灌进四张图
 */
function applyHistoryToCharts(points)
{
    let filled = buildFilledHistorySeries(points || [], currentHistoryRange);

    setChartData(cpuChart, filled.labels, [filled.cpu]);
    setChartData(ramChart, filled.labels, [filled.ram]);
    setChartData(diskChart, filled.labels, [filled.disk]);
    setChartData(gpuChart, filled.labels, [filled.gpu, filled.vram]);
}

/**
 * 按原始采样聚合，避免固定时间槽与 8s 采样错位产生「假 0」。
 * - 10m：每条记录一个点
 * - 1h：每 6 条平均融成一个点
 * - 3h：每 16 条平均融成一个点
 * 从右侧对齐完整分组，左侧不足一组的余数丢弃；标签取该组最后一条时间。
 */
function buildFilledHistorySeries(points, range)
{
    let windowMs = rangeToMillis(range);
    let chunkSize = rangeToChunkSize(range);
    let cutoff = Date.now() - windowMs;

    let sorted = (points || []).filter(function(point)
    {
        return point && point.timestamp > cutoff;
    }).sort(function(a, b)
    {
        return a.timestamp - b.timestamp;
    });

    let labels = [];
    let cpu = [];
    let ram = [];
    let disk = [];
    let gpu = [];
    let vram = [];

    // 右侧对齐：余数留在最左侧并丢弃，保证每组都是完整 chunkSize 条
    let startIndex = sorted.length % chunkSize;

    for (let i = startIndex; i < sorted.length; i += chunkSize)
    {
        let chunk = sorted.slice(i, i + chunkSize);
        let averaged = averageHistoryChunk(chunk);
        let anchor = chunk[chunk.length - 1];

        labels.push(formatHistoryTime(anchor.timestamp));
        cpu.push(averaged.processor);
        ram.push(averaged.ram);
        disk.push(averaged.storage);
        gpu.push(averaged.gpu);
        vram.push(averaged.vram);
    }

    return {
        labels: labels,
        cpu: cpu,
        ram: ram,
        disk: disk,
        gpu: gpu,
        vram: vram
    };
}

function rangeToChunkSize(range)
{
    switch (range)
    {
        case "1h":
            return 6;
        case "3h":
            return 16;
        case "10m":
        default:
            return 1;
    }
}

function averageHistoryChunk(chunk)
{
    let total = {
        processor: 0,
        ram: 0,
        storage: 0,
        gpu: 0,
        vram: 0
    };

    for (let i = 0; i < chunk.length; i++)
    {
        total.processor += Number(chunk[i].processor) || 0;
        total.ram += Number(chunk[i].ram) || 0;
        total.storage += Number(chunk[i].storage) || 0;
        total.gpu += Number(chunk[i].gpu) || 0;
        total.vram += Number(chunk[i].vram) || 0;
    }

    let count = chunk.length;
    return {
        processor: total.processor / count,
        ram: total.ram / count,
        storage: total.storage / count,
        gpu: total.gpu / count,
        vram: total.vram / count
    };
}

function rangeToMillis(range)
{
    switch (range)
    {
        case "1h":
            return 60 * 60 * 1000;
        case "3h":
            return 3 * 60 * 60 * 1000;
        case "10m":
        default:
            return 10 * 60 * 1000;
    }
}

function setChartData(chart, labels, seriesList)
{
    if (!chart)
    {
        return;
    }

    chart.data.labels = labels;
    for (let i = 0; i < seriesList.length; i++)
    {
        chart.data.datasets[i].data = seriesList[i];
    }
    chart.update(0);
}

function formatHistoryTime(timestamp)
{
    let date = new Date(timestamp);
    return pad2(date.getHours()) + ":" + pad2(date.getMinutes()) + ":" + pad2(date.getSeconds());
}

function pad2(value)
{
    return (value < 10 ? "0" : "") + value;
}

function buildDataset(borderColor, backgroundColor, label)
{
    return {
        label: label,
        borderWidth: 1.5,
        borderColor: borderColor,
        pointRadius: 0,
        pointHoverRadius: 4,
        pointHitRadius: 10,
        pointBackgroundColor: "rgba(255, 255, 255, 1)",
        backgroundColor: backgroundColor,
        data: []
    };
}

function formatUsagePercent(value)
{
    let number = Number(value);
    if (isNaN(number))
    {
        return "—";
    }
    return (Math.round(number * 10) / 10) + "%";
}

function buildCommonOptions()
{
    return {
        maintainAspectRatio: false,
        legend:
        {
            display: false
        },
        // 悬停显示时间点与利用率百分比；沿纵线吸附，不必精确点到折线
        tooltips:
        {
            enabled: true,
            mode: "index",
            intersect: false,
            callbacks:
            {
                title: function(tooltipItems)
                {
                    if (!tooltipItems || tooltipItems.length === 0)
                    {
                        return "";
                    }
                    return tooltipItems[0].label || "";
                },
                label: function(tooltipItem, data)
                {
                    let datasetLabel = data.datasets[tooltipItem.datasetIndex].label || "";
                    return datasetLabel + ": " + formatUsagePercent(tooltipItem.yLabel);
                }
            }
        },
        hover:
        {
            mode: "index",
            intersect: false
        },
        elements:
        {
            line:
            {
                tension: 0
            }
        },
        scales:
        {
            yAxes:
            [
                {
                    ticks:
                    {
                        display: true,
                        suggestedMin: 0,
                        suggestedMax: 100,
                        stepSize: 25,
                        fontSize: 10,
                        callback: function(value)
                        {
                            return value + "%";
                        }
                    },
                    gridLines:
                    {
                        drawTicks: true
                    }
                }
            ],
            xAxes:
            [
                {
                    ticks:
                    {
                        display: true,
                        autoSkip: true,
                        maxTicksLimit: 8,
                        fontSize: 10,
                        maxRotation: 0,
                        minRotation: 0
                    },
                    gridLines:
                    {
                        drawTicks: true
                    }
                }
            ]
        },
        animation:
        {
            duration: 0
        }
    };
}
