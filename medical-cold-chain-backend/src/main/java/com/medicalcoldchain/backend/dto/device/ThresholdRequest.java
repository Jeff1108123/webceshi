package com.medicalcoldchain.backend.dto.device;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class ThresholdRequest {

    @NotNull(message = "温度下限不能为空")
    private Double tempMin;

    @NotNull(message = "温度上限不能为空")
    private Double tempMax;

    @NotNull(message = "湿度下限不能为空")
    private Double humidityMin;

    @NotNull(message = "湿度上限不能为空")
    private Double humidityMax;

    @NotNull(message = "光照上限不能为空")
    @Positive(message = "光照上限必须大于 0")
    private Double lightMax;

    @NotNull(message = "失效时长不能为空")
    @Positive(message = "失效时长必须大于 0")
    private Integer durationLimitHours;
}
