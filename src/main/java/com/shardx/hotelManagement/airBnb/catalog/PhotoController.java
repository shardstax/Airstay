package com.shardx.hotelManagement.airBnb.catalog;

import com.shardx.hotelManagement.airBnb.exception.BadRequestException;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@RestController
public class PhotoController {

    private static final String PHOTO_PATH = "/api/v1/photos/";
    private static final Pattern STORED_PATH = Pattern.compile(PHOTO_PATH
            + "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final int MAX_DIMENSION = 4096;
    private final JdbcTemplate jdbc;

    public PhotoController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @ApiResponse(responseCode = "201", description = "Photo stored")
    @PostMapping(path = "/admin/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UploadedPhoto> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty() || file.getSize() > MAX_BYTES) {
            throw new BadRequestException("Choose an image up to 2 MB");
        }
        try {
            byte[] content = file.getBytes();
            String mediaType = validateImage(content);
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO catalog_photo (id, media_type, content) VALUES (?, ?, ?)", id, mediaType, content);
            return ResponseEntity.status(201).body(new UploadedPhoto(PHOTO_PATH + id));
        } catch (IOException e) {
            throw invalidImage();
        }
    }

    @GetMapping(path = "/photos/{id}", produces = {MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE})
    public ResponseEntity<byte[]> read(@PathVariable UUID id) {
        return jdbc.query("SELECT media_type, content FROM catalog_photo WHERE id = ?", (row, index) ->
                        ResponseEntity.ok()
                                .contentType(MediaType.parseMediaType(row.getString("media_type")))
                                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                                .body(row.getBytes("content")), id)
                .stream().findFirst().orElseGet(() -> ResponseEntity.notFound().build());
    }

    static boolean isPhotoPath(String value) {
        return value != null && STORED_PATH.matcher(value).matches();
    }

    private static String validateImage(byte[] content) throws IOException {
        try (var input = new javax.imageio.stream.MemoryCacheImageInputStream(new ByteArrayInputStream(content))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw invalidImage();
            }
            var reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!format.equals("jpeg") && !format.equals("png")) {
                    throw invalidImage();
                }
                reader.setInput(input, true, true);
                if (reader.getWidth(0) > MAX_DIMENSION || reader.getHeight(0) > MAX_DIMENSION) {
                    throw new BadRequestException("Photo dimensions must be at most 4096 pixels per side");
                }
                reader.read(0);
                return "image/" + format;
            } finally {
                reader.dispose();
            }
        }
    }

    private static BadRequestException invalidImage() {
        return new BadRequestException("Choose a valid JPEG or PNG image");
    }

    public record UploadedPhoto(String url) {}
}
