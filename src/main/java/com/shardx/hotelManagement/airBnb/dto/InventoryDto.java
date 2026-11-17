package com.shardx.hotelManagement.airBnb.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class InventoryDto {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate date;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer bookedCount;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer reservedCount;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer totalCount;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal surgeFactor;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal price;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean closed;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
