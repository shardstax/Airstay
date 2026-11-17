package com.shardx.hotelManagement.airBnb.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/** Reads a Kafka topic from the beginning with a throw-away consumer group, for tests. */
public class KafkaTopicReader {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(40);

    private final String bootstrapServers;
    private final ObjectMapper objectMapper;

    public KafkaTopicReader(String bootstrapServers, ObjectMapper objectMapper) {
        this.bootstrapServers = bootstrapServers;
        this.objectMapper = objectMapper;
    }

    /** Waits for the booking's event of the given type and returns its JSON. */
    public JsonNode awaitEvent(String topic, long bookingId, String type) {
        ConsumerRecord<String, String> record = awaitRecord(topic, String.valueOf(bookingId), type, DEFAULT_TIMEOUT);
        try {
            return objectMapper.readTree(record.value());
        } catch (Exception e) {
            throw new AssertionError("Unreadable " + type + " event on " + topic + ": " + record.value(), e);
        }
    }

    /** Waits for a record with this key (and this event-type header, unless null) and returns it. */
    public ConsumerRecord<String, String> awaitRecord(String topic, String key, String eventType, Duration timeout) {
        try (KafkaConsumer<String, String> consumer = newConsumer(topic)) {
            long deadline = System.currentTimeMillis() + timeout.toMillis();
            while (System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (matches(record, key, eventType)) {
                        return record;
                    }
                }
            }
        }
        throw new AssertionError("No " + (eventType == null ? "message" : eventType + " event") + " with key " + key
                + " arrived on " + topic + " within " + timeout.toSeconds() + " s");
    }

    /** Every matching record that shows up within the given time (to check that something happened exactly once). */
    public List<ConsumerRecord<String, String>> readAll(String topic, String key, String eventType, Duration listenFor) {
        List<ConsumerRecord<String, String>> found = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = newConsumer(topic)) {
            long deadline = System.currentTimeMillis() + listenFor.toMillis();
            while (System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (matches(record, key, eventType)) {
                        found.add(record);
                    }
                }
            }
        }
        return found;
    }

    private boolean matches(ConsumerRecord<String, String> record, String key, String eventType) {
        if (!key.equals(record.key())) {
            return false;
        }
        if (eventType == null) {
            return true;
        }
        Header header = record.headers().lastHeader("event-type");
        return header != null && eventType.equals(new String(header.value(), StandardCharsets.UTF_8));
    }

    private KafkaConsumer<String, String> newConsumer(String topic) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-reader-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(List.of(topic));
        return consumer;
    }
}
