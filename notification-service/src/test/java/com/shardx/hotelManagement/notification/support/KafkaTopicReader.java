package com.shardx.hotelManagement.notification.support;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Reads a whole topic from the beginning (no consumer group, so it never disturbs the listeners under test) and looks
 * for a message. A fresh reader per call keeps the tests independent of each other.
 */
public final class KafkaTopicReader {

    private final String bootstrapServers;

    public KafkaTopicReader(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    /** The first message (value) on the topic that matches, polling until the timeout. */
    public Optional<String> awaitRecord(String topic, Predicate<String> match, Duration timeout) {
        return findRecord(topic, match, timeout).map(ConsumerRecord::value);
    }

    /** Same, but the whole record, so a test can look at its timestamp. */
    public Optional<ConsumerRecord<String, String>> findRecord(String topic, Predicate<String> match,
                                                              Duration timeout) {
        try (KafkaConsumer<String, String> consumer = newConsumer()) {
            List<TopicPartition> partitions = partitionsOf(consumer, topic);
            if (partitions.isEmpty()) {
                return Optional.empty();
            }
            consumer.assign(partitions);
            consumer.seekToBeginning(partitions);

            long deadline = System.nanoTime() + timeout.toNanos();
            while (System.nanoTime() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(250))) {
                    if (match.test(record.value())) {
                        return Optional.of(record);
                    }
                }
            }
            return Optional.empty();
        }
    }

    /**
     * Whether the topic exists. A test that says "nothing is in topic X" must check this first, otherwise renaming
     * the topic would make the test pass without proving anything.
     */
    public boolean topicExists(String topic) {
        try (KafkaConsumer<String, String> consumer = newConsumer()) {
            return !partitionsOf(consumer, topic).isEmpty();
        }
    }

    private KafkaConsumer<String, String> newConsumer() {
        return new KafkaConsumer<>(Map.<String, Object>of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
    }

    private static List<TopicPartition> partitionsOf(KafkaConsumer<String, String> consumer, String topic) {
        List<PartitionInfo> infos = consumer.partitionsFor(topic);
        if (infos == null) {
            return List.of();
        }
        return infos.stream().map(info -> new TopicPartition(topic, info.partition())).toList();
    }
}
