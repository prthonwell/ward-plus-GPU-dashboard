package org.bsoftware.ward.dto;

import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;

/**
 * /api/usage/history 响应：区间标识 + 采样点列表
 */
@Getter
@Setter
public class UsageHistoryDto
{
    /**
     * 查询区间：10m / 1h / 3h
     */
    private String range;

    /**
     * 落在区间内的采样点（按时间升序）
     */
    private List<UsageHistoryPointDto> points = new ArrayList<>();
}
