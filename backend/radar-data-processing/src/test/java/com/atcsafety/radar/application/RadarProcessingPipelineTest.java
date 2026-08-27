package com.atcsafety.radar.application;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.atcsafety.contracts.RadarPosition;
import com.atcsafety.radar.enrichment.CoordinateDeriver;
import com.atcsafety.radar.loading.RadarDumpLoader;
import com.atcsafety.radar.validation.RadarPositionValidator;
import com.atcsafety.radar.validation.ValidationResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RadarProcessingPipelineTest {

    @Mock
    private RadarDumpLoader radarDumpLoader;

    @Mock
    private CoordinateDeriver coordinateDeriver;

    @Mock
    private RadarPositionValidator validator;

    @Mock
    private RadarPositionPublisher publisher;

    @Mock
    private InterCycleDelay interCycleDelay;

    private RadarProcessingPipeline pipeline;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        pipeline = new RadarProcessingPipeline(
                radarDumpLoader, coordinateDeriver, validator, publisher, interCycleDelay);

        logAppender = new ListAppender<>();
        logAppender.start();
        Logger logger = (Logger) LoggerFactory.getLogger(RadarProcessingPipeline.class);
        logger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        Logger logger = (Logger) LoggerFactory.getLogger(RadarProcessingPipeline.class);
        logger.detachAppender(logAppender);
    }

    @Nested
    class Run {

        @Test
        void should_log_exactly_fifty_entries_when_fifty_positions_are_valid() throws Exception {
            List<RadarPosition> positions = IntStream.range(0, 50)
                    .mapToObj(i -> validPosition(i + 1))
                    .toList();

            when(radarDumpLoader.load()).thenReturn(positions);
            when(coordinateDeriver.derive(any())).thenAnswer(inv -> inv.getArgument(0));
            when(validator.validate(any())).thenReturn(new ValidationResult.Valid());

            pipeline.run(new DefaultApplicationArguments());

            verify(publisher, times(50)).publish(any(RadarPosition.class));
        }

        @Test
        void should_not_log_invalid_positions() throws Exception {
            RadarPosition validPos = validPosition(1);
            RadarPosition invalidPos1 = validPosition(2);
            RadarPosition invalidPos2 = validPosition(3);

            when(radarDumpLoader.load()).thenReturn(List.of(validPos, invalidPos1, invalidPos2));
            when(coordinateDeriver.derive(any())).thenAnswer(inv -> inv.getArgument(0));
            when(validator.validate(validPos)).thenReturn(new ValidationResult.Valid());
            when(validator.validate(invalidPos1))
                    .thenReturn(new ValidationResult.Invalid("flightNb is missing or blank"));
            when(validator.validate(invalidPos2))
                    .thenReturn(new ValidationResult.Invalid("lat=91.0 is outside [-90, 90]"));

            pipeline.run(new DefaultApplicationArguments());

            verify(publisher, times(1)).publish(validPos);
            verify(publisher, never()).publish(invalidPos1);
            verify(publisher, never()).publish(invalidPos2);
        }

        @Test
        void should_continue_processing_remaining_records_when_one_throws() throws Exception {
            List<RadarPosition> positions = IntStream.range(1, 11)
                    .mapToObj(RadarProcessingPipelineTest::validPosition)
                    .toList();

            when(radarDumpLoader.load()).thenReturn(positions);
            when(coordinateDeriver.derive(any())).thenAnswer(inv -> {
                RadarPosition pos = inv.getArgument(0);
                if (pos.targetId() == 5) {
                    throw new RuntimeException("Simulated processing failure for targetId=5");
                }
                return pos;
            });
            when(validator.validate(any())).thenReturn(new ValidationResult.Valid());

            pipeline.run(new DefaultApplicationArguments());

            // Records 1–4 and 6–10 must be published (9 records); record 5 must be skipped
            verify(publisher, times(9)).publish(any(RadarPosition.class));
            verify(publisher, never()).publish(positions.get(4)); // index 4 = targetId 5
        }

        @Test
        void should_publish_flush_exactly_once_after_all_positions_are_published() throws Exception {
            List<RadarPosition> positions = List.of(validPosition(1), validPosition(2), validPosition(3));

            when(radarDumpLoader.load()).thenReturn(positions);
            when(coordinateDeriver.derive(any())).thenAnswer(inv -> inv.getArgument(0));
            when(validator.validate(any())).thenReturn(new ValidationResult.Valid());

            pipeline.run(new DefaultApplicationArguments());

            // All 3 positions published first, then exactly one flush
            verify(publisher, times(3)).publish(any(RadarPosition.class));
            verify(publisher, times(1)).publishFlush(anyInt());
        }

        @Test
        void should_publish_flush_with_last_published_cycle_when_multiple_cycles_present() throws Exception {
            RadarPosition cycle3pos = positionWithCycle(1, 3);
            RadarPosition cycle5pos = positionWithCycle(2, 5);

            when(radarDumpLoader.load()).thenReturn(List.of(cycle3pos, cycle5pos));
            when(coordinateDeriver.derive(any())).thenAnswer(inv -> inv.getArgument(0));
            when(validator.validate(any())).thenReturn(new ValidationResult.Valid());

            pipeline.run(new DefaultApplicationArguments());

            // lastCycle = 5, so flush radarCycle = 5
            verify(publisher, times(1)).publishFlush(5);
        }

        @Test
        void should_publish_flush_with_cycle_zero_when_no_valid_positions_were_published() throws Exception {
            RadarPosition invalidPos = validPosition(1);

            when(radarDumpLoader.load()).thenReturn(List.of(invalidPos));
            when(coordinateDeriver.derive(any())).thenAnswer(inv -> inv.getArgument(0));
            when(validator.validate(any()))
                    .thenReturn(new ValidationResult.Invalid("flightNb is missing or blank"));

            pipeline.run(new DefaultApplicationArguments());

            verify(publisher, never()).publish(any(RadarPosition.class));
            verify(publisher, times(1)).publishFlush(0);
        }

        @Test
        void should_invoke_delay_once_per_cycle_excluding_flush_sentinel() throws Exception {
            // 3 real cycles — delay fires at the 2 boundaries between them (not before the flush)
            List<RadarPosition> positions = List.of(
                    positionWithCycle(1, 1),
                    positionWithCycle(2, 2),
                    positionWithCycle(3, 3));

            when(radarDumpLoader.load()).thenReturn(positions);
            when(coordinateDeriver.derive(any())).thenAnswer(inv -> inv.getArgument(0));
            when(validator.validate(any())).thenReturn(new ValidationResult.Valid());

            pipeline.run(new DefaultApplicationArguments());

            verify(interCycleDelay, times(2)).delay();
        }

        @Test
        void should_log_error_with_exception_type_and_target_id_when_record_throws() throws Exception {
            RadarPosition faultyPosition = validPosition(42);

            when(radarDumpLoader.load()).thenReturn(List.of(faultyPosition));
            when(coordinateDeriver.derive(any()))
                    .thenThrow(new IllegalArgumentException("bad coordinate data"));

            pipeline.run(new DefaultApplicationArguments());

            assertThat(logAppender.list)
                    .filteredOn(e -> e.getLevel() == Level.ERROR)
                    .hasSize(1);

            String errorMessage = logAppender.list.stream()
                    .filter(e -> e.getLevel() == Level.ERROR)
                    .findFirst()
                    .orElseThrow()
                    .getFormattedMessage();

            assertThat(errorMessage).contains("42");                          // targetId
            assertThat(errorMessage).contains("IllegalArgumentException");    // exception type
        }
    }

    // ---- helpers ----

    private static RadarPosition validPosition(int targetId) {
        return new RadarPosition(
                targetId,
                1,
                10000.0f,
                20000.0f,
                35000.0f,
                450.0f,
                "2026-03-26T10:00:00Z",
                180,
                "AF" + (1000 + targetId),
                48.5f,
                2.2f
        );
    }

    private static RadarPosition positionWithCycle(int targetId, int radarCycle) {
        return new RadarPosition(
                targetId,
                radarCycle,
                10000.0f,
                20000.0f,
                35000.0f,
                450.0f,
                "2026-03-26T10:00:00Z",
                180,
                "AF" + (1000 + targetId),
                48.5f,
                2.2f
        );
    }
}
