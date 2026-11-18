package com.shardx.hotelManagement.airBnb.ai;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** A safe, transparent intermediate response for the chat UI to confirm or clarify. */
@Schema(description = "Parsed hotel-search filters and any required clarification")
public record AiHotelSearchInterpretation(
        AiHotelSearchIntent intent,
        List<String> missingFields,
        List<String> unsupportedFilters,
        boolean readyForBasicSearch
) {
}
