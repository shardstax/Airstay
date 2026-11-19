package com.shardx.hotelManagement.airBnb.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.shardx.hotelManagement.airBnb.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

@TestPropertySource(properties = "airstay.ai.search.enabled=true")
class AiSearchSettingsIT extends IntegrationTestBase {

    private static final String SETTINGS = "/admin/ai-search/settings";
    private static final String STATUS = "/ai/hotel-search/status";

    @MockitoBean
    private ChatModel chatModel;

    @Test
    void onlySiteAdminsCanDisableTheDefaultOnAssistant() {
        assertThat(data(get(STATUS, null)).path("available").asBoolean()).isTrue();
        assertThat(get(SETTINGS, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(update(newManagerToken(), false).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        String email = "site-admin-" + UUID.randomUUID() + "@example.com";
        post("/auth/signup", null, Map.of("name", "Site Admin", "email", email, "password", PASSWORD,
                "accountType", "ADMIN"));
        String token = data(post("/auth/login", null, Map.of("email", email, "password", PASSWORD)))
                .path("accessToken").asText();
        assertThat(update(token, false).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        jdbc.update("INSERT INTO app_user_roles (user_id, role) SELECT id, 'ADMIN' FROM app_user WHERE email = ?", email);

        try {
            assertThat(data(update(token, false)).path("enabled").asBoolean()).isFalse();
            assertThat(data(get(STATUS, null)).path("available").asBoolean()).isFalse();
            assertThat(plainPost(newPlainClient(), "/ai/hotel-search/interpret", null,
                    Map.of("message", "A hotel in Pune")).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            verifyNoInteractions(chatModel);
        } finally {
            assertThat(update(token, true).getStatusCode()).isEqualTo(HttpStatus.OK);
        }
        assertThat(data(get(STATUS, null)).path("available").asBoolean()).isTrue();
    }

    private ResponseEntity<JsonNode> update(String token, boolean enabled) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return newPlainClient().exchange(SETTINGS, HttpMethod.PUT,
                new HttpEntity<>(Map.of("enabled", enabled), headers), JsonNode.class);
    }
}
