package com.atcsafety.radar.application;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.atcsafety.contracts.RadarPosition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RadarPositionPublisherTest {

    private static final String TOPIC = "radar.validated-positions";

    @Mock
    private KafkaTemplate<String, RadarPositionMessage> kafkaTemplate;

    private RadarPositionPublisher publisher;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        publisher = new RadarPositionPublisher(kafkaTemplate, TOPIC);

        logAppender = new ListAppender<>();
        logAppender.start();
        Logger logger = (Logger) LoggerFactory.getLogger(RadarPositionPublisher.class);
        logger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        Logger logger = (Logger) LoggerFactory.getLogger(RadarPositionPublisher.class);
        logger.detachAppender(logAppender);
    }

    @Nested
    class Publish {

        @Test
        @SuppressWarnings("unchecked")
        void should_send_message_to_configured_topic_when_position_is_valid() {
            RadarPosition position = samplePosition(1, 3, "AF123");
            when(kafkaTemplate.send(any(), any(), any()))
                    .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

            publisher.publish(position);

            verify(kafkaTemplate, times(1))
                    .send(eq(TOPIC), eq("AF123"), any(RadarPositionMessage.class));
        }

        @Test
        @SuppressWarnings("unchecked")
        void should_map_all_fields_from_radar_position_to_message() {
            RadarPosition position = new RadarPosition(
                    42,      // targetId
                    7,       // radarCycle
                    11000.0f, // x
                    22000.0f, // y
                    35000.0f, // altFeet
                    480.0f,  // speedKn
                    "2026-03-26T10:00:00Z", // timestampUTC
                    90,      // headingDeg
                    "BA456", // flightNb
                    48.9f,   // lat
                    2.3f     // lon
            );
            when(kafkaTemplate.send(any(), any(), any()))
                    .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

            publisher.publish(position);

            ArgumentCaptor<RadarPositionMessage> captor =
                    ArgumentCaptor.forClass(RadarPositionMessage.class);
            verify(kafkaTemplate).send(eq(TOPIC), eq("BA456"), captor.capture());

            RadarPositionMessage msg = captor.getValue();
            assertThat(msg.targetId()).isEqualTo(42);
            assertThat(msg.radarCycle()).isEqualTo(7);
            assertThat(msg.flightNb()).isEqualTo("BA456");
            assertThat(msg.x()).isEqualTo(11000.0f);
            assertThat(msg.y()).isEqualTo(22000.0f);
            assertThat(msg.altFeet()).isEqualTo(35000.0f);
            assertThat(msg.speedKn()).isEqualTo(480.0f);
            assertThat(msg.headingDeg()).isEqualTo(90);
            assertThat(msg.lat()).isEqualTo(48.9f);
            assertThat(msg.lon()).isEqualTo(2.3f);
            assertThat(msg.timestampUTC()).isEqualTo("2026-03-26T10:00:00Z");
        }

        @Test
        void should_log_error_with_target_id_and_radar_cycle_when_send_fails() {
            RadarPosition position = samplePosition(99, 5, "LH789");
            CompletableFuture<SendResult<String, RadarPositionMessage>> failedFuture =
                    CompletableFuture.failedFuture(new RuntimeException("broker down"));
            when(kafkaTemplate.send(any(), any(), any())).thenReturn(failedFuture);

            publisher.publish(position);

            assertThat(logAppender.list)
                    .filteredOn(e -> e.getLevel() == Level.ERROR)
                    .hasSize(1);

            String errorMessage = logAppender.list.stream()
                    .filter(e -> e.getLevel() == Level.ERROR)
                    .findFirst()
                    .orElseThrow()
                    .getFormattedMessage();

            assertThat(errorMessage).contains("99");
            assertThat(errorMessage).contains("5");
        }

        @Test
        void should_not_propagate_exception_when_send_fails() {
            RadarPosition position = samplePosition(10, 2, "EK100");
            CompletableFuture<SendResult<String, RadarPositionMessage>> failedFuture =
                    CompletableFuture.failedFuture(new RuntimeException("broker unavailable"));
            when(kafkaTemplate.send(any(), any(), any())).thenReturn(failedFuture);

            assertThatCode(() -> publisher.publish(position)).doesNotThrowAnyException();
        }
    }

    @Nested
    class PublishFlush {

        @Test
        @SuppressWarnings("unchecked")
        void should_send_flush_message_with_sentinel_values_when_last_cycle_is_ten() {
            when(kafkaTemplate.send(any(), any(), any()))
                    .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

            publisher.publishFlush(10);

            ArgumentCaptor<RadarPositionMessage> captor =
                    ArgumentCaptor.forClass(RadarPositionMessage.class);
            verify(kafkaTemplate).send(eq(TOPIC), eq("__FLUSH__"), captor.capture());

            RadarPositionMessage msg = captor.getValue();
            assertThat(msg.targetId()).isEqualTo(-1);
            assertThat(msg.flightNb()).isEqualTo("__FLUSH__");
            assertThat(msg.radarCycle()).isEqualTo(11);
            assertThat(msg.headingDeg()).isEqualTo(0);
        }

        @Test
        @SuppressWarnings("unchecked")
        void should_use_flush_sentinel_as_key_when_publishing_flush_message() {
            when(kafkaTemplate.send(any(), any(), any()))
                    .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

            publisher.publishFlush(5);

            verify(kafkaTemplate, times(1))
                    .send(eq(TOPIC), eq("__FLUSH__"), any(RadarPositionMessage.class));
        }

        @Test
        void should_log_error_and_not_propagate_when_flush_send_fails() {
            CompletableFuture<SendResult<String, RadarPositionMessage>> failedFuture =
                    CompletableFuture.failedFuture(new RuntimeException("broker down during flush"));
            when(kafkaTemplate.send(any(), any(), any())).thenReturn(failedFuture);

            assertThatCode(() -> publisher.publishFlush(3)).doesNotThrowAnyException();

            assertThat(logAppender.list)
                    .filteredOn(e -> e.getLevel() == Level.ERROR)
                    .hasSize(1);
        }
    }

    // ---- helpers ----

    private static RadarPosition samplePosition(int targetId, int radarCycle, String flightNb) {
        return new RadarPosition(
                targetId,
                radarCycle,
                10000.0f,
                20000.0f,
                35000.0f,
                450.0f,
                "2026-03-26T10:00:00Z",
                180,
                flightNb,
                48.5f,
                2.2f
        );
    }
}
