package com.shardx.hotelManagement.airBnb.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;

import java.util.List;

@Schema(description = "Natural-language hotel search request")
public record AiHotelSearchRequest(
        @NotBlank @Size(max = 800)
        @Schema(example = "Two rooms for two guests each in Pune on 19 and 20 November, breakfast and spa, budget 12000")
        String message,
        @Size(max = 12) List<@NotNull @Valid Turn> history
) {
    public AiHotelSearchRequest {
        if (history == null) history = List.of();
    }

    public record Turn(@NotBlank @Size(max = 800) String message,
                       @NotBlank @Size(max = 800) String reply) {
    }
}
