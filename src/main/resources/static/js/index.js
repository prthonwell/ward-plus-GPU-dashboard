"use strict";

/**
 * 仪表盘页初始化：绑定 DOM、启动 usage 长轮询与 info 定时刷新、注册翻页控件
 */
function indexInitialization()
{
    logoPage = document.getElementById("logo-page");
    contactsPage = document.getElementById("contacts-page");

    showCards();

    currentClockSpeed = document.getElementById("currentClockSpeed");
    currentProcCount = document.getElementById("currentProcCount");
    currentTotalStorage = document.getElementById("currentTotalStorage");
    currentDiskCount = document.getElementById("currentDiskCount");

    currentPage = 1;
    firstControl = document.getElementById("first-control");
    secondControl = document.getElementById("second-control");

    cloudLeft = document.getElementById("cloud-left");
    cloudRight = document.getElementById("cloud-right");

    days = document.getElementById("uptime-days");
    hours = document.getElementById("uptime-hours");
    minutes = document.getElementById("uptime-minutes");
    seconds = document.getElementById("uptime-seconds");

    usageXHR = new XMLHttpRequest();
    infoXHR = new XMLHttpRequest();

    // 图表：进入页拉一次历史；之后仅点击区间/Refresh 才刷新（不再长轮询）
    loadUsageHistory();

    setInterval(function()
    {
        sendInfoRequest();
    }, 1000);

    firstControl.addEventListener("click", function(event) {changePage(event.target || event.srcElement)});
    secondControl.addEventListener("click", function(event) {changePage(event.target || event.srcElement)});
}

/**
 * Changes cards opacity with random sequence
 */
function showCards()
{
    contactsPage.style.visibility = "hidden";

    let cards = document.getElementsByClassName("card");
    let versionLabel = document.getElementById("project-version");

    let randomSequenceArray = getRandomSequenceArray();

    for (let i = 0; i < cards.length; i++)
    {
        setTimeout(function()
        {
            cards[randomSequenceArray[i]].style.opacity = "1";

            // 最后一张为图表卡，同时淡入版本号
            if (randomSequenceArray[i] == (cards.length - 1))
            {
                versionLabel.style.opacity = "1";
            }
        }, 70 * i);
    }
}

/**
 * Generates random sequence
 */
function getRandomSequenceArray()
{
    let buffer = [];
    let cardsCount = document.getElementsByClassName("card").length;

    while (buffer.length < cardsCount)
    {
        let randomNumber = Math.floor(Math.random() * cardsCount);

        if ((buffer.indexOf(randomNumber) === -1))
        {
            buffer.push(randomNumber);
        }
    }

    return buffer;
}

/**
 * 请求 /api/info：刷新时钟频率、进程数、磁盘总量与 uptime 数字
 */
function sendInfoRequest()
{
    infoXHR.onreadystatechange = function()
    {
        if ((this.readyState == 4) && (this.status == 200))
        {
            let response = JSON.parse(this.response);

            currentClockSpeed.innerHTML = response.processor.clockSpeed;
            currentProcCount.innerHTML = response.machine.procCount;
            currentTotalStorage.innerHTML = response.storage.total;
            currentDiskCount.innerHTML = response.storage.diskCount;

            days.innerHTML = response.uptime.days;
            hours.innerHTML = response.uptime.hours;
            minutes.innerHTML = response.uptime.minutes;
            seconds.innerHTML = response.uptime.seconds;
        }
    }

    infoXHR.open("GET", "/api/info");
    infoXHR.send();
}

/**
 * Changes page
 *
 * @param {*} control element
 */
function changePage(element)
{
    if ((String(element.id) == "first-control") && (currentPage > 1))
    {
        currentPage -= 1;
        setCloudAnimation(currentPage);
    }
    else if ((String(element.id) == "second-control") && (currentPage < 2))
    {
        currentPage += 1;
        setCloudAnimation(currentPage);
    }

    setPageVisibility(currentPage);
    setControlOpacity(currentPage);
}

/**
 * Changes page visibility
 *
 * @param {*} new page
 */
function setPageVisibility(newPage)
{
    switch (newPage)
    {
        case 1:
        {
            logoPage.style.visibility = "";
            contactsPage.style.visibility = "hidden";
            break;
        }
        case 2:
        {
            logoPage.style.visibility = "hidden";
            contactsPage.style.visibility = "";
            break;
        }
    }
}

/**
 * Animates clouds
 *
 * @param {*} new page
 */
function setCloudAnimation(newSquareScale)
{
    switch (newSquareScale)
    {
        case 1:
        {
            cloudLeft.style.animation = "fade-in-cloud-left 0.3s forwards";
            cloudRight.style.animation = "fade-in-cloud-right 0.3s forwards";
            break;
        }
        case 2:
        {
            cloudLeft.style.animation = "fade-out-cloud-left 0.3s forwards";
            cloudRight.style.animation = "fade-out-cloud-right 0.3s forwards";
            break;
        }
    }
}

/**
 * Changes opacity of control
 *
 * @param {*} new page
 */
function setControlOpacity(newSquareScale)
{
    switch (newSquareScale)
    {
        case 1:
        {
            firstControl.style.opacity = "0.5";
            secondControl.style.opacity = "1";
            break;
        }
        case 2:
        {
            firstControl.style.opacity = "1";
            secondControl.style.opacity = "0.5";
            break;
        }
    }
}