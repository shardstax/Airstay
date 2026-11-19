package com.shardx.hotelManagement.airBnb.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiSearchSettings {

    private final JdbcTemplate jdbc;
    private final AiSearchProperties properties;
    private final ObjectProvider<ChatModel> chatModels;

    public Settings current() {
        boolean enabled = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT enabled FROM ai_search_settings WHERE id = 1", Boolean.class));
        return new Settings(enabled, isConfigured());
    }

    public Settings update(boolean enabled) {
        jdbc.update("UPDATE ai_search_settings SET enabled = ? WHERE id = 1", enabled);
        return new Settings(enabled, isConfigured());
    }

    public boolean isAvailable() {
        return isConfigured() && Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT enabled FROM ai_search_settings WHERE id = 1", Boolean.class));
    }

    private boolean isConfigured() {
        return properties.isEnabled() && chatModels.getIfAvailable() != null;
    }

    public record Settings(boolean enabled, boolean configured) {}
}
