package com.shardx.hotelManagement.airBnb.events;

import com.shardx.hotelManagement.airBnb.entity.OutboxEvent;
import com.shardx.hotelManagement.airBnb.repository.OutboxEventRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-01T10:00:00Z"), ZoneOffset.UTC);

    @Mock private OutboxEventRepository repository;
    @Mock private KafkaTemplate<String, String> kafka;
    @Mock private TransactionTemplate transactions;

    private final EventProperties properties = new EventProperties();
    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        relay = new OutboxRelay(repository, kafka, properties, transactions, CLOCK);
    }

    private static OutboxEvent event(String key, String type) {
        return new OutboxEvent(UUID.randomUUID(), Topics.BOOKING_EVENTS, key, type, "{\"bookingId\":" + key + "}",
                LocalDateTime.now(CLOCK));
    }

    private static CompletableFuture<SendResult<String, String>> acknowledged() {
        return CompletableFuture.completedFuture(null);
    }

    private static CompletableFuture<SendResult<String, String>> refused() {
        return CompletableFuture.failedFuture(new KafkaException("broker unavailable"));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void publishesPendingEventsInOrderAndMarksThemPublished() {
        List<OutboxEvent> batch = List.of(event("1", "BOOKING_CONFIRMED"), event("2", "BOOKING_CANCELLED"),
                event("3", "BOOKING_EXPIRED"));
        when(repository.lockNextBatch(properties.getBatchSize())).thenReturn(batch);
        when(kafka.send(any(ProducerRecord.class))).thenReturn(acknowledged());

        int published = relay.publishBatch();

        assertThat(published).isEqualTo(3);
        ArgumentCaptor<ProducerRecord<String, String>> sent = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka, times(3)).send(sent.capture());
        assertThat(sent.getAllValues()).extracting(ProducerRecord::key).containsExactly("1", "2", "3");
        assertThat(batch).allSatisfy(e -> assertThat(e.getPublishedAt()).isEqualTo(LocalDateTime.now(CLOCK)));

        // Consumers get the event id and type as headers, so they can de-duplicate and route without parsing the body.
        ProducerRecord<String, String> first = sent.getAllValues().get(0);
        assertThat(new String(first.headers().lastHeader("event-id").value(), StandardCharsets.UTF_8))
                .isEqualTo(batch.get(0).getEventId().toString());
        assertThat(new String(first.headers().lastHeader("event-type").value(), StandardCharsets.UTF_8))
                .isEqualTo("BOOKING_CONFIRMED");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void stopsAtTheFirstRefusalSoLaterEventsNeverOvertakeEarlierOnes() {
        List<OutboxEvent> batch = List.of(event("1", "BOOKING_CONFIRMED"), event("2", "BOOKING_CANCELLED"),
                event("3", "BOOKING_EXPIRED"));
        when(repository.lockNextBatch(properties.getBatchSize())).thenReturn(batch);
        when(kafka.send(any(ProducerRecord.class))).thenReturn(acknowledged()).thenReturn(refused());

        int published = relay.publishBatch();

        assertThat(published).isEqualTo(1);
        assertThat(batch.get(0).getPublishedAt()).isNotNull();
        assertThat(batch.get(1).getPublishedAt()).as("refused: retried next round").isNull();
        assertThat(batch.get(2).getPublishedAt()).as("must not be sent before event 2").isNull();
        verify(kafka, times(2)).send(any(ProducerRecord.class)); // event 3 was never even attempted
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void aSendThatFailsImmediatelyIsHandledTheSameWay() {
        List<OutboxEvent> batch = List.of(event("1", "BOOKING_CONFIRMED"));
        when(repository.lockNextBatch(properties.getBatchSize())).thenReturn(batch);
        when(kafka.send(any(ProducerRecord.class))).thenThrow(new KafkaException("cannot reach the broker"));

        int published = relay.publishBatch();

        assertThat(published).isZero();
        assertThat(batch.get(0).getPublishedAt()).isNull();
    }

    @Test
    void doesNothingAtAllWhenSwitchedOff() {
        properties.setEnabled(false);

        relay.publishPending();

        verifyNoInteractions(repository, kafka, transactions);
    }

    @Test
    void keepsPublishingWhileBatchesAreFullAndStopsWhenTheyAreNot() {
        properties.setBatchSize(2);
        when(transactions.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null));
        when(repository.lockNextBatch(2))
                .thenReturn(List.of(event("1", "BOOKING_CONFIRMED"), event("2", "BOOKING_CONFIRMED")))
                .thenReturn(List.of(event("3", "BOOKING_CONFIRMED")));
        when(kafka.send(any(ProducerRecord.class))).thenReturn(acknowledged());

        relay.publishPending();

        verify(repository, times(2)).lockNextBatch(2); // a full batch means "there may be more"; a short one means done
        verify(repository, never()).countByPublishedAtIsNull();
    }
}
