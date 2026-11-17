package com.shardx.hotelManagement.airBnb.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class HotelReportDto {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long bookingCount;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal totalRevenue;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal avgRevenue;
}
