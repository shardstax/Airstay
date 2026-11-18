package com.shardx.hotelManagement.airBnb.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.shardx.hotelManagement.airBnb.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PhotoIT extends IntegrationTestBase {

    @Test
    void managerCanUploadAndUsePhotoOnHotelAndRoom() throws IOException {
        String manager = newManagerToken();
        byte[] image = png();
        var uploaded = upload(manager, image);
        assertThat(uploaded.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String path = data(uploaded).path("url").asText();
        assertThat(path).startsWith("/api/v1/photos/");

        var publicImage = rest.getForEntity(path.substring("/api/v1".length()), byte[].class);
        assertThat(publicImage.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(publicImage.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(publicImage.getBody()).isEqualTo(image);

        var hotel = post("/admin/hotels", manager,
                Map.of("name", "Photo Hotel", "placeId", newPlaceId(), "photos", List.of(path)));
        assertThat(hotel.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(data(hotel).path("photos").get(0).asText()).isEqualTo(path);

        long hotelId = data(hotel).path("id").asLong();
        var room = post("/admin/hotels/" + hotelId + "/rooms", manager,
                Map.of("type", "Deluxe", "basePrice", 1800, "totalCount", 2, "capacity", 2, "photos", List.of(path)));
        assertThat(room.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(data(room).path("photos").get(0).asText()).isEqualTo(path);
    }

    @Test
    void guestCannotUploadAndManagerCannotUploadNonImages() throws IOException {
        assertThat(upload(newGuestToken(), png()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(upload(newManagerToken(), "text".getBytes(StandardCharsets.UTF_8)).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void missingPhotoReturnsNotFound() {
        assertThat(rest.getForEntity("/photos/" + UUID.randomUUID(), byte[].class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<JsonNode> upload(String token, byte[] image) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(token);
        var form = new LinkedMultiValueMap<String, Object>();
        form.add("file", new ByteArrayResource(image) {
            @Override
            public String getFilename() {
                return "photo.png";
            }
        });
        return rest.postForEntity("/admin/photos", new HttpEntity<>(form, headers), JsonNode.class);
    }

    private static byte[] png() throws IOException {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
        return bytes.toByteArray();
    }
}
