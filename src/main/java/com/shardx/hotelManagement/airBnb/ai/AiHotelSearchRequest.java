package com.shardx.hotelManagement.airBnb.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Natural-language hotel search request")
public record AiHotelSearchRequest(
        @NotBlank @Size(max = 800)
        @Schema(example = "Two rooms for two guests each in Pune on 19 and 20 November, breakfast and spa, budget 12000")
        String message
) {
}
