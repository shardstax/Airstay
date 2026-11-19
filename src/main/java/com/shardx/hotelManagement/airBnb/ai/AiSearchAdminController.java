package com.shardx.hotelManagement.airBnb.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/ai-search/settings")
@RequiredArgsConstructor
public class AiSearchAdminController {
    private final AiSearchSettings settings;

    @GetMapping
    public ResponseEntity<AiSearchSettings.Settings> getAiSearchSettings() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(settings.current());
    }

    @PutMapping
    public ResponseEntity<AiSearchSettings.Settings> setAiSearchSettings(@Valid @RequestBody AiSearchUpdate request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(settings.update(request.enabled()));
    }

    public record AiSearchUpdate(@NotNull Boolean enabled) {}
}
