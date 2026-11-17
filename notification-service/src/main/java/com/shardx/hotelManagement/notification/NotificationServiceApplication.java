package com.shardx.hotelManagement.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Listens to AirStay's booking events on Kafka and e-mails the guest. It never calls the AirStay API: every event is a
 * self-contained snapshot (see docs/events.md at the repository root).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
