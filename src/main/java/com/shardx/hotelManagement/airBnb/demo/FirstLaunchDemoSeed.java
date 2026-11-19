package com.shardx.hotelManagement.airBnb.demo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "airstay.demo.seed-on-startup", havingValue = "true")
public class FirstLaunchDemoSeed implements ApplicationRunner {

    private static final String OWNER_EMAIL = "railway-demo-inventory@airstay.invalid";
    private static final String CATALOG_START = "WITH demo_hotels";

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    @Value("${airstay.demo.directory:/app/demo}")
    private Path directory;

    @Override
    @Transactional(rollbackFor = IOException.class)
    public void run(ApplicationArguments args) throws IOException {
        // A competing startup waits on this insert. If the seed fails, the entire transaction rolls back.
        if (jdbc.update("INSERT INTO demo_catalog_seed (id) VALUES (1) ON CONFLICT DO NOTHING") == 0) {
            return;
        }

        if (jdbc.queryForObject("SELECT COUNT(*) FROM hotel", Long.class) != 0) {
            throw new IllegalStateException("First-launch demo seeding requires an empty hotel catalog");
        }

        // Demo inventory has an owner for data integrity. The random password is never stored in plaintext or logged.
        Long ownerId = jdbc.queryForObject("""
                INSERT INTO app_user (email, password, name) VALUES (?, ?, 'AirStay Demo Catalog') RETURNING id
                """, Long.class, OWNER_EMAIL, passwordEncoder.encode(UUID.randomUUID().toString()));
        if (ownerId == null) throw new IllegalStateException("The demo owner could not be created");
        jdbc.update("INSERT INTO app_user_roles (user_id, role) VALUES (?, 'HOTEL_MANAGER')", ownerId);

        String seed = Files.readString(directory.resolve("seed-demo-catalog.sql"));
        int start = seed.indexOf(CATALOG_START);
        if (start < 0) throw new IllegalStateException("Could not find the shared demo catalog SQL");
        execute(seed.substring(start), ownerId);

        for (String row : Files.readAllLines(directory.resolve("demo-photos.csv")).stream().skip(1).toList()) {
            String[] photo = row.split(",", 2);
            byte[] content = Files.readAllBytes(directory.resolve("demo-assets").resolve(photo[0]));
            jdbc.update("INSERT INTO catalog_photo (id, media_type, content) VALUES (?, 'image/jpeg', ?)",
                    UUID.fromString(photo[1]), content);
        }
        execute(Files.readString(directory.resolve("attach-demo-photos.sql")), ownerId);
        log.info("First-launch demo catalog and photos imported");
    }

    private void execute(String sql, long ownerId) {
        byte[] script = sql.replace(":demo_owner_id", Long.toString(ownerId)).getBytes(StandardCharsets.UTF_8);
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            ScriptUtils.executeSqlScript(connection, new ByteArrayResource(script));
            return null;
        });
    }
}
