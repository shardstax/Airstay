package com.shardx.hotelManagement.airBnb.events;

import com.shardx.hotelManagement.airBnb.entity.OutboxEvent;
import com.shardx.hotelManagement.airBnb.repository.OutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Publishes the outbox to Kafka.
 *
 * <ul>
 *   <li><b>Order:</b> rows are read in id order and publishing stops at the first failure, so a booking's events are never
 *       delivered out of order (they also share a Kafka key, hence a partition).</li>
 *   <li><b>At-least-once:</b> a row is marked published only after Kafka acknowledged it. A crash in between publishes it
 *       again, which is why every event has a unique id for consumers to de-duplicate on.</li>
 *   <li><b>Kafka down:</b> nothing breaks. Bookings keep working, events pile up in the outbox and flow out when Kafka
 *       is back. Each round gives up after a few seconds, so it cannot hold a scheduler thread for long.</li>
 *   <li><b>Several instances:</b> rows are claimed with FOR UPDATE SKIP LOCKED, so instances share the work.</li>
 * </ul>
 */
@Component
@Slf4j
public class OutboxRelay {

    private static final int MAX_BATCHES_PER_ROUND = 20;

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafka;
    private final EventProperties properties;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public OutboxRelay(OutboxEventRepository repository, KafkaTemplate<String, String> kafka,
                       EventProperties properties, TransactionTemplate transactions, Clock clock) {
        this.repository = repository;
        this.kafka = kafka;
        this.properties = properties;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${airstay.events.relay-interval-ms}")
    public void publishPending() {
        if (!properties.isEnabled()) {
            return;
        }
        for (int batch = 0; batch < MAX_BATCHES_PER_ROUND; batch++) {
            Integer published = transactions.execute(status -> publishBatch());
            if (published == null || published < properties.getBatchSize()) {
                return; // nothing left, or Kafka refused something: try again next round
            }
        }
    }

    /** Publishes one batch and returns how many rows were published. Called inside one database transaction. */
    int publishBatch() {
        List<OutboxEvent> batch = repository.lockNextBatch(properties.getBatchSize());
        int published = 0;
        for (OutboxEvent event : batch) {
            try {
                ProducerRecord<String, String> record =
                        new ProducerRecord<>(event.getTopic(), event.getEventKey(), event.getPayload());
                record.headers().add(new RecordHeader("event-id", event.getEventId().toString().getBytes(StandardCharsets.UTF_8)));
                record.headers().add(new RecordHeader("event-type", event.getEventType().getBytes(StandardCharsets.UTF_8)));
                kafka.send(record).get(properties.getSendTimeoutMs(), TimeUnit.MILLISECONDS);
                event.setPublishedAt(LocalDateTime.now(clock));
                published++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // Kafka unreachable or refused: keep this and every later row for the next round (preserves order).
                log.warn("Could not publish outbox event {} ({}), will retry: {}", event.getId(), event.getEventType(),
                        e.toString());
                break;
            }
        }
        return published;
    }

    /** Published rows are only kept for a while: Kafka itself is the long-term record. */
    @Scheduled(cron = "0 15 * * * *")
    public void deleteOldPublishedEvents() {
        Integer deleted = transactions.execute(status ->
                repository.deletePublishedBefore(LocalDateTime.now(clock).minusDays(properties.getRetentionDays())));
        if (deleted != null && deleted > 0) {
            log.info("Deleted {} published outbox events older than {} days", deleted, properties.getRetentionDays());
        }
    }
}
