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
 * 把历史点灌进四张图：按当前区间铺满整段横轴，缺失采样填 0
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
 * 方案 A：约 50 个点铺满整个所选区间（如 3h → 12:20～15:20）。
 * step = range/49；右端对齐到 floor(now/step)*step，避免刷新时旧点挪槽。
 * 每个窗口 (t-step, t] 取最新采样，没有则 0。
 */
function buildFilledHistorySeries(points, range)
{
    let targetPoints = 50;
    let windowMs = rangeToMillis(range);
    let stepMs = Math.max(1, Math.floor(windowMs / (targetPoints - 1)));
    let end = Math.floor(Date.now() / stepMs) * stepMs;

    let sorted = (points || []).slice().sort(function(a, b)
    {
        return a.timestamp - b.timestamp;
    });

    let labels = [];
    let cpu = [];
    let ram = [];
    let disk = [];
    let gpu = [];
    let vram = [];
    let searchIndex = 0;

    for (let i = 0; i < targetPoints; i++)
    {
        // 第 0 点 = end - 49*step，最后一点 = 对齐后的 end
        let slotEnd = end - (targetPoints - 1 - i) * stepMs;
        let slotStart = slotEnd - stepMs;

        labels.push(formatHistoryTime(slotEnd));

        while (searchIndex < sorted.length && sorted[searchIndex].timestamp <= slotStart)
        {
            searchIndex++;
        }

        let chosen = null;
        let probe = searchIndex;
        while (probe < sorted.length && sorted[probe].timestamp <= slotEnd)
        {
            chosen = sorted[probe];
            probe++;
        }

        if (chosen)
        {
            cpu.push(chosen.processor);
            ram.push(chosen.ram);
            disk.push(chosen.storage);
            gpu.push(chosen.gpu);
            vram.push(chosen.vram);
        }
        else
        {
            cpu.push(0);
            ram.push(0);
            disk.push(0);
            gpu.push(0);
            vram.push(0);
        }
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
        pointHoverRadius: 3,
        pointBackgroundColor: "rgba(255, 255, 255, 1)",
        backgroundColor: backgroundColor,
        data: []
    };
}

function buildCommonOptions()
{
    return {
        maintainAspectRatio: false,
        legend:
        {
            display: false
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
