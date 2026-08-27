package com.atcsafety.restapi.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.atcsafety.restapi.domain.ReviewConflictException;
import com.atcsafety.restapi.domain.ReviewStatus;
import com.atcsafety.restapi.domain.ReviewValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventServiceTest {

    private InfringementEventPort eventPort;
    private EventService eventService;

    @BeforeEach
    void setUp() {
        eventPort = mock(InfringementEventPort.class);
        eventService = new EventService(eventPort);
    }

    @Nested
    class GetSummary {

        @Test
        void should_return_zero_counts_when_no_events_exist() {
            when(eventPort.countWithMongoStatuses(any())).thenReturn(0L);

            EventSummaryResponse summary = eventService.getSummary();

            assertThat(summary.pendingCount()).isZero();
            assertThat(summary.escalatedCount()).isZero();
        }

        @Test
        void should_count_closed_documents_as_pending_review() {
            when(eventPort.countWithMongoStatuses(List.of("CLOSED", "PENDING_REVIEW"))).thenReturn(7L);
            when(eventPort.countWithMongoStatuses(List.of("ESCALATED"))).thenReturn(3L);

            EventSummaryResponse summary = eventService.getSummary();

            assertThat(summary.pendingCount()).isEqualTo(7L);
            assertThat(summary.escalatedCount()).isEqualTo(3L);
        }

        @Test
        void should_query_for_both_closed_and_pending_review_mongo_statuses_when_counting_pending() {
            when(eventPort.countWithMongoStatuses(any())).thenReturn(0L);

            eventService.getSummary();

            verify(eventPort).countWithMongoStatuses(List.of("CLOSED", "PENDING_REVIEW"));
        }
    }

    @Nested
    class ListEvents {

        @Test
        void should_reject_unrecognised_sort_field_to_prevent_injection() {
            assertThatThrownBy(() -> eventService.listEvents("PENDING_REVIEW", 0, 20, "arbitraryField", "asc"))
                    .isInstanceOf(InvalidSortFieldException.class);
        }

        @Test
        void should_reject_unrecognised_status_with_status_specific_exception() {
            assertThatThrownBy(() -> eventService.listEvents("UNKNOWN_STATUS", 0, 20, null, null))
                    .isInstanceOf(InvalidStatusException.class)
                    .hasMessageContaining("UNKNOWN_STATUS");
        }

        @Test
        void should_return_events_filtered_by_status() {
            var data = eventDataWithStatus("id1", "PENDING_REVIEW");
            when(eventPort.countWithMongoStatuses(any())).thenReturn(1L);
            when(eventPort.findPageWithMongoStatuses(any(), anyInt(), anyInt(), any(), any()))
                    .thenReturn(List.of(data));

            PagedResponse<EventListItemResponse> response = eventService.listEvents("PENDING_REVIEW", 0, 20, "startedAt", "asc");

            assertThat(response.content()).hasSize(1);
            assertThat(response.content().get(0).id()).isEqualTo("id1");
        }

        @Test
        void should_map_closed_mongo_status_to_pending_review_in_list_response() {
            var data = eventDataWithStatus("id1", "CLOSED");
            when(eventPort.countWithMongoStatuses(any())).thenReturn(1L);
            when(eventPort.findPageWithMongoStatuses(any(), anyInt(), anyInt(), any(), any()))
                    .thenReturn(List.of(data));

            PagedResponse<EventListItemResponse> response = eventService.listEvents("PENDING_REVIEW", 0, 20, "startedAt", "asc");

            assertThat(response.content().get(0).status()).isEqualTo("PENDING_REVIEW");
        }

        @Test
        void should_build_correct_pagination_metadata_for_multi_page_result() {
            when(eventPort.countWithMongoStatuses(any())).thenReturn(45L);
            when(eventPort.findPageWithMongoStatuses(any(), anyInt(), anyInt(), any(), any()))
                    .thenReturn(List.of());

            PagedResponse<EventListItemResponse> response = eventService.listEvents("PENDING_REVIEW", 0, 20, "startedAt", "asc");

            assertThat(response.totalElements()).isEqualTo(45L);
            assertThat(response.totalPages()).isEqualTo(3);
            assertThat(response.page()).isZero();
            assertThat(response.size()).isEqualTo(20);
        }

        @Test
        void should_allow_sort_by_escalated_at() {
            when(eventPort.countWithMongoStatuses(any())).thenReturn(0L);
            when(eventPort.findPageWithMongoStatuses(any(), anyInt(), anyInt(), any(), any()))
                    .thenReturn(List.of());

            assertThatNoException().isThrownBy(
                    () -> eventService.listEvents("ESCALATED", 0, 20, "escalatedAt", "asc"));
        }

        @Test
        void should_allow_null_sort_field_for_default_ordering() {
            when(eventPort.countWithMongoStatuses(any())).thenReturn(0L);
            when(eventPort.findPageWithMongoStatuses(any(), anyInt(), anyInt(), any(), any()))
                    .thenReturn(List.of());

            assertThatNoException().isThrownBy(
                    () -> eventService.listEvents("PENDING_REVIEW", 0, 20, null, null));
        }

        @Test
        void should_return_zero_total_pages_when_no_events_exist() {
            when(eventPort.countWithMongoStatuses(any())).thenReturn(0L);
            when(eventPort.findPageWithMongoStatuses(any(), anyInt(), anyInt(), any(), any()))
                    .thenReturn(List.of());

            PagedResponse<EventListItemResponse> response = eventService.listEvents("PENDING_REVIEW", 0, 20, null, null);

            assertThat(response.totalElements()).isZero();
            assertThat(response.totalPages()).isZero();
        }
    }

    @Nested
    class ToListItem {

        @Test
        void should_derive_comment_count_from_stored_comments_without_additional_query() {
            var comments = List.of(
                    new CommentData("c1", "text", "J. Smith", Instant.parse("2026-01-01T10:00:00Z")),
                    new CommentData("c2", "text", "J. Smith", Instant.parse("2026-01-01T10:01:00Z")),
                    new CommentData("c3", "text", "J. Smith", Instant.parse("2026-01-01T10:02:00Z")));
            var data = new InfringementEventData(
                    "id1", "AF100", "BA200",
                    Instant.parse("2026-01-01T10:00:00Z"), Instant.parse("2026-01-01T10:00:25Z"),
                    "CLOSED", null, null, null, 2.1, 450.0, comments);
            when(eventPort.countWithMongoStatuses(any())).thenReturn(1L);
            when(eventPort.findPageWithMongoStatuses(any(), anyInt(), anyInt(), any(), any()))
                    .thenReturn(List.of(data));

            PagedResponse<EventListItemResponse> response = eventService.listEvents("PENDING_REVIEW", 0, 20, null, null);

            assertThat(response.content().get(0).commentCount()).isEqualTo(3);
        }

        @Test
        void should_produce_summary_string_with_one_decimal_nm_and_integer_ft_when_both_separations_present() {
            var data = new InfringementEventData(
                    "id1", "AF100", "BA200",
                    Instant.parse("2026-01-01T10:00:00Z"), Instant.parse("2026-01-01T10:00:25Z"),
                    "CLOSED", null, null, null, 2.1, 450.0, List.of());
            when(eventPort.countWithMongoStatuses(any())).thenReturn(1L);
            when(eventPort.findPageWithMongoStatuses(any(), anyInt(), anyInt(), any(), any()))
                    .thenReturn(List.of(data));

            PagedResponse<EventListItemResponse> response = eventService.listEvents("PENDING_REVIEW", 0, 20, null, null);

            assertThat(response.content().get(0).summary()).isEqualTo("Min sep: 2.1 NM / 450 ft");
        }

        @Test
        void should_return_null_summary_when_either_separation_is_null() {
            var dataWithNullHorizontal = new InfringementEventData(
                    "id1", "AF100", "BA200",
                    Instant.parse("2026-01-01T10:00:00Z"), Instant.parse("2026-01-01T10:00:25Z"),
                    "CLOSED", null, null, null, null, 450.0, List.of());
            when(eventPort.countWithMongoStatuses(any())).thenReturn(1L);
            when(eventPort.findPageWithMongoStatuses(any(), anyInt(), anyInt(), any(), any()))
                    .thenReturn(List.of(dataWithNullHorizontal));

            PagedResponse<EventListItemResponse> response = eventService.listEvents("PENDING_REVIEW", 0, 20, null, null);

            assertThat(response.content().get(0).summary()).isNull();
        }

        @Test
        void should_include_static_type_string_in_list_item_response() {
            var data = eventDataWithStatus("id1", "CLOSED");
            when(eventPort.countWithMongoStatuses(any())).thenReturn(1L);
            when(eventPort.findPageWithMongoStatuses(any(), anyInt(), anyInt(), any(), any()))
                    .thenReturn(List.of(data));

            PagedResponse<EventListItemResponse> response = eventService.listEvents("PENDING_REVIEW", 0, 20, null, null);

            assertThat(response.content().get(0).type()).isEqualTo("SeparationMinimaInfringement");
        }
    }

    @Nested
    class GetEventDetail {

        @Test
        void should_return_404_when_event_not_found() {
            when(eventPort.findById("unknown-id")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> eventService.getEventDetail("unknown-id"))
                    .isInstanceOf(EventNotFoundException.class);
        }

        @Test
        void should_return_trajectories_ordered_by_radar_cycle_ascending() {
            var detail = detailWithTrajectories(
                    List.of(positionAt(3), positionAt(1), positionAt(2)),
                    List.of(positionAt(3), positionAt(1), positionAt(2)));
            when(eventPort.findById("evt-001")).thenReturn(Optional.of(detail));

            EventDetailResponse response = eventService.getEventDetail("evt-001");

            assertThat(response.trajectory1()).extracting(TrajectoryPositionData::radarCycle)
                    .containsExactly(1L, 2L, 3L);
            assertThat(response.trajectory2()).extracting(TrajectoryPositionData::radarCycle)
                    .containsExactly(1L, 2L, 3L);
        }

        @Test
        void should_include_grace_period_positions_in_trajectory() {
            // 3 active cycles + 3 grace-period cycles = 6 total
            var positions = List.of(
                    positionAt(1), positionAt(2), positionAt(3),
                    positionAt(4), positionAt(5), positionAt(6));
            var detail = detailWithTrajectories(positions, positions);
            when(eventPort.findById("evt-001")).thenReturn(Optional.of(detail));

            EventDetailResponse response = eventService.getEventDetail("evt-001");

            assertThat(response.trajectory1()).hasSize(6);
            assertThat(response.trajectory2()).hasSize(6);
        }

        @Test
        void should_sort_comments_by_id_ascending_when_created_at_timestamps_are_identical() {
            Instant sameTime = Instant.parse("2026-01-01T10:30:00Z");
            var detail = detailWithComments(List.of(
                    new CommentData("c3", "third", "J. Smith", sameTime),
                    new CommentData("c1", "first", "J. Smith", sameTime),
                    new CommentData("c2", "second", "J. Smith", sameTime)));
            when(eventPort.findById("evt-001")).thenReturn(Optional.of(detail));

            EventDetailResponse response = eventService.getEventDetail("evt-001");

            assertThat(response.comments()).extracting(CommentData::id)
                    .containsExactly("c1", "c2", "c3");
        }

        @Test
        void should_map_closed_mongo_status_to_pending_review_in_detail_response() {
            var detail = detailWithStatus("CLOSED");
            when(eventPort.findById("evt-001")).thenReturn(Optional.of(detail));

            EventDetailResponse response = eventService.getEventDetail("evt-001");

            assertThat(response.status()).isEqualTo("PENDING_REVIEW");
        }

        @Test
        void should_preserve_trajectory_alignment_after_sort() {
            // trajectory1[i] and trajectory2[i] represent the same radarCycle (alignment contract)
            var detail = detailWithTrajectories(
                    List.of(positionAt(3), positionAt(1), positionAt(2)),
                    List.of(positionAt(3), positionAt(1), positionAt(2)));
            when(eventPort.findById("evt-001")).thenReturn(Optional.of(detail));

            EventDetailResponse response = eventService.getEventDetail("evt-001");

            for (int i = 0; i < response.trajectory1().size(); i++) {
                assertThat(response.trajectory1().get(i).radarCycle())
                        .isEqualTo(response.trajectory2().get(i).radarCycle());
            }
        }

        // =====================================================================
        // SAFM-76: eventStartCycle / eventEndCycle trajectory window fields
        // =====================================================================

        @Test
        void should_pass_event_start_cycle_through_to_response_when_present() {
            var detail = detailWithTrajectoriesAndEventBounds(
                    List.of(positionAt(1), positionAt(2), positionAt(3)),
                    List.of(positionAt(1), positionAt(2), positionAt(3)),
                    3, 7);
            when(eventPort.findById("evt-001")).thenReturn(Optional.of(detail));

            EventDetailResponse response = eventService.getEventDetail("evt-001");

            assertThat(response.eventStartCycle()).isEqualTo(3);
        }

        @Test
        void should_pass_event_end_cycle_through_to_response_when_present() {
            var detail = detailWithTrajectoriesAndEventBounds(
                    List.of(positionAt(1), positionAt(2), positionAt(3)),
                    List.of(positionAt(1), positionAt(2), positionAt(3)),
                    3, 7);
            when(eventPort.findById("evt-001")).thenReturn(Optional.of(detail));

            EventDetailResponse response = eventService.getEventDetail("evt-001");

            assertThat(response.eventEndCycle()).isEqualTo(7);
        }

        @Test
        void should_return_null_event_start_cycle_for_events_written_before_safm_76() {
            // Pre-SAFM-76 events have no eventStartCycle in MongoDB — field is absent/null
            var detail = detailWithTrajectoriesAndEventBounds(
                    List.of(positionAt(1), positionAt(2)),
                    List.of(positionAt(1), positionAt(2)),
                    null, null);
            when(eventPort.findById("evt-001")).thenReturn(Optional.of(detail));

            EventDetailResponse response = eventService.getEventDetail("evt-001");

            assertThat(response.eventStartCycle()).isNull();
            assertThat(response.eventEndCycle()).isNull();
        }

        @Test
        void should_support_zero_pre_event_positions_when_event_start_cycle_is_zero() {
            // Cold-start: no history buffered yet, so no pre-event positions prepended.
            // eventStartCycle == 0 means the infringement window starts at index 0.
            var detail = detailWithTrajectoriesAndEventBounds(
                    List.of(positionAt(1), positionAt(2)),
                    List.of(positionAt(1), positionAt(2)),
                    0, 1);
            when(eventPort.findById("evt-001")).thenReturn(Optional.of(detail));

            EventDetailResponse response = eventService.getEventDetail("evt-001");

            assertThat(response.eventStartCycle()).isZero();
            assertThat(response.eventEndCycle()).isEqualTo(1);
        }

        @Test
        void should_propagate_author_from_comment_data_to_response() {
            Instant t = Instant.parse("2026-01-01T10:30:00Z");
            var detail = detailWithComments(List.of(new CommentData("c1", "text", "J. Smith", t)));
            when(eventPort.findById("evt-001")).thenReturn(Optional.of(detail));

            EventDetailResponse response = eventService.getEventDetail("evt-001");

            assertThat(response.comments().get(0).author()).isEqualTo("J. Smith");
        }
    }

    private InfringementEventData eventDataWithStatus(String id, String mongoStatus) {
        return new InfringementEventData(
                id, "AF100", "BA200",
                Instant.parse("2026-01-01T10:00:00Z"),
                Instant.parse("2026-01-01T10:00:25Z"),
                mongoStatus, null, null, null, 2.1, 450.0, List.of());
    }

    private TrajectoryPositionData positionAt(long radarCycle) {
        return new TrajectoryPositionData(radarCycle, Instant.parse("2026-01-01T10:00:00Z"),
                60000.0, 100000.0, 20000.0, 48.946, 2.442, null);
    }

    private InfringementEventDetailData detailWithTrajectories(
            List<TrajectoryPositionData> traj1, List<TrajectoryPositionData> traj2) {
        return new InfringementEventDetailData(
                "evt-001", "AF100", "BA200",
                Instant.parse("2026-01-01T10:00:00Z"),
                Instant.parse("2026-01-01T10:00:25Z"),
                "CLOSED", null, null, null, 2.1, 450.0, 2, null, null, null,
                traj1, traj2, List.of());
    }

    private InfringementEventDetailData detailWithTrajectoriesAndEventBounds(
            List<TrajectoryPositionData> traj1, List<TrajectoryPositionData> traj2,
            Integer eventStartCycle, Integer eventEndCycle) {
        return new InfringementEventDetailData(
                "evt-001", "AF100", "BA200",
                Instant.parse("2026-01-01T10:00:00Z"),
                Instant.parse("2026-01-01T10:00:25Z"),
                "CLOSED", null, null, null, 2.1, 450.0, 2, null, eventStartCycle, eventEndCycle,
                traj1, traj2, List.of());
    }

    private InfringementEventDetailData detailWithComments(List<CommentData> comments) {
        return new InfringementEventDetailData(
                "evt-001", "AF100", "BA200",
                Instant.parse("2026-01-01T10:00:00Z"),
                Instant.parse("2026-01-01T10:00:25Z"),
                "CLOSED", null, null, null, 2.1, 450.0, null, null, null, null,
                List.of(), List.of(), comments);
    }

    private InfringementEventDetailData detailWithStatus(String mongoStatus) {
        return new InfringementEventDetailData(
                "evt-001", "AF100", "BA200",
                Instant.parse("2026-01-01T10:00:00Z"),
                Instant.parse("2026-01-01T10:00:25Z"),
                mongoStatus, null, null, null, 2.1, 450.0, null, null, null, null,
                List.of(), List.of(), List.of());
    }

    private InfringementEventDetailData detailWithReviewStatus(String id, String mongoStatus) {
        return new InfringementEventDetailData(
                id, "AF100", "BA200",
                Instant.parse("2026-01-01T10:00:00Z"),
                Instant.parse("2026-01-01T10:00:25Z"),
                mongoStatus, null, null, null, 2.1, 450.0, null, null, null, null,
                List.of(), List.of(), List.of());
    }

    @Nested
    class ToDetailResponse {

        @Test
        void should_set_event_start_cycle_to_zero_and_event_end_cycle_to_trajectory_size_minus_one() {
            // AC-5: simple event with no pre/post context — eventStartCycle=0, eventEndCycle=size-1
            var positions = List.of(positionAt(1), positionAt(2), positionAt(3));
            var detail = detailWithTrajectoriesAndEventBounds(positions, positions, 0, 2);
            when(eventPort.findById("evt-001")).thenReturn(Optional.of(detail));

            EventDetailResponse response = eventService.getEventDetail("evt-001");

            assertThat(response.eventStartCycle()).isZero();
            assertThat(response.eventEndCycle()).isEqualTo(2);
        }

        @Test
        void should_pass_min_vertical_separation_cycle_index_through_to_response() {
            // AC-3: minVerticalSeparationCycleIndex is present in the response when available
            var detail = new InfringementEventDetailData(
                    "evt-001", "AF100", "BA200",
                    Instant.parse("2026-01-01T10:00:00Z"),
                    Instant.parse("2026-01-01T10:00:25Z"),
                    "CLOSED", null, null, null, 2.1, 450.0, 2, 5, null, null,
                    List.of(), List.of(), List.of());
            when(eventPort.findById("evt-001")).thenReturn(Optional.of(detail));

            EventDetailResponse response = eventService.getEventDetail("evt-001");

            assertThat(response.minVerticalSeparationCycleIndex()).isEqualTo(5);
        }

        @Test
        void should_return_null_min_vertical_separation_cycle_index_for_events_written_before_safm_64() {
            // AC-6: backward compatibility — events without this field must not NPE
            var detail = new InfringementEventDetailData(
                    "evt-001", "AF100", "BA200",
                    Instant.parse("2026-01-01T10:00:00Z"),
                    Instant.parse("2026-01-01T10:00:25Z"),
                    "CLOSED", null, null, null, 2.1, 450.0, 2, null, null, null,
                    List.of(), List.of(), List.of());
            when(eventPort.findById("evt-001")).thenReturn(Optional.of(detail));

            EventDetailResponse response = eventService.getEventDetail("evt-001");

            assertThat(response.minVerticalSeparationCycleIndex()).isNull();
        }
    }

    @Nested
    class ChangeStatus {

        @Test
        void should_return_dismissed_response_with_dismissed_at_set_when_dismissing_pending_event() {
            var detail = detailWithReviewStatus("evt-001", "PENDING_REVIEW");
            when(eventPort.findById("evt-001")).thenReturn(Optional.of(detail));
            when(eventPort.changeStatus(any(), any(), any(), any(), any())).thenReturn(true);

            ChangeStatusResponse response = eventService.changeStatus("evt-001", "DISMISSED", null);

            assertThat(response.status()).isEqualTo("DISMISSED");
            assertThat(response.dismissedAt()).isNotNull();
            assertThat(response.escalationReason()).isNull();
            assertThat(response.escalatedAt()).isNull();
        }

        @Test
        void should_return_escalated_response_with_escalated_at_and_reason_when_escalating_pending_event() {
            var detail = detailWithReviewStatus("evt-001", "PENDING_REVIEW");
            when(eventPort.findById("evt-001")).thenReturn(Optional.of(detail));
            when(eventPort.changeStatus(any(), any(), any(), any(), any())).thenReturn(true);

            ChangeStatusResponse response = eventService.changeStatus("evt-001", "ESCALATED", "Near-miss at FL290");

            assertThat(response.status()).isEqualTo("ESCALATED");
            assertThat(response.escalatedAt()).isNotNull();
            assertThat(response.escalationReason()).isEqualTo("Near-miss at FL290");
            assertThat(response.dismissedAt()).isNull();
        }

        @Test
        void should_throw_validation_exception_when_escalating_without_reason() {
            var detail = detailWithReviewStatus("evt-001", "PENDING_REVIEW");
            when(eventPort.findById("evt-001")).thenReturn(Optional.of(detail));

            assertThatThrownBy(() -> eventService.changeStatus("evt-001", "ESCALATED", null))
                    .isInstanceOf(ReviewValidationException.class);
        }

        @Test
        void should_throw_validation_exception_when_escalating_with_blank_reason() {
            var detail = detailWithReviewStatus("evt-001", "PENDING_REVIEW");
            when(eventPort.findById("evt-001")).thenReturn(Optional.of(detail));

            assertThatThrownBy(() -> eventService.changeStatus("evt-001", "ESCALATED", "   "))
                    .isInstanceOf(ReviewValidationException.class);
        }

        @Test
        void should_throw_event_not_found_exception_when_event_does_not_exist() {
            when(eventPort.findById("unknown-id")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> eventService.changeStatus("unknown-id", "DISMISSED", null))
                    .isInstanceOf(EventNotFoundException.class);
        }

        @Test
        void should_throw_invalid_status_exception_when_status_is_not_recognised() {
            var detail = detailWithReviewStatus("evt-001", "PENDING_REVIEW");
            when(eventPort.findById("evt-001")).thenReturn(Optional.of(detail));

            assertThatThrownBy(() -> eventService.changeStatus("evt-001", "UNKNOWN_STATUS", null))
                    .isInstanceOf(InvalidStatusException.class);
        }

        @Test
        void should_throw_conflict_exception_when_event_is_already_dismissed() {
            var detail = detailWithReviewStatus("evt-001", "DISMISSED");
            when(eventPort.findById("evt-001")).thenReturn(Optional.of(detail));

            assertThatThrownBy(() -> eventService.changeStatus("evt-001", "DISMISSED", null))
                    .isInstanceOf(ReviewConflictException.class);
        }

        @Test
        void should_throw_conflict_exception_when_event_is_already_escalated() {
            var detail = detailWithReviewStatus("evt-001", "ESCALATED");
            when(eventPort.findById("evt-001")).thenReturn(Optional.of(detail));

            assertThatThrownBy(() -> eventService.changeStatus("evt-001", "ESCALATED", "reason"))
                    .isInstanceOf(ReviewConflictException.class);
        }
    }

    @Nested
    class AddComment {

        @Test
        void should_reject_null_comment_text() {
            assertThatThrownBy(() -> eventService.addComment("evt-001", null, "J. Smith"))
                    .isInstanceOf(CommentValidationException.class);
        }

        @Test
        void should_reject_empty_comment_text() {
            assertThatThrownBy(() -> eventService.addComment("evt-001", "", "J. Smith"))
                    .isInstanceOf(CommentValidationException.class);
        }

        @Test
        void should_reject_blank_comment_text() {
            assertThatThrownBy(() -> eventService.addComment("evt-001", "   ", "J. Smith"))
                    .isInstanceOf(CommentValidationException.class);
        }

        @Test
        void should_reject_comment_exceeding_1000_characters() {
            String tooLong = "a".repeat(1001);
            assertThatThrownBy(() -> eventService.addComment("evt-001", tooLong, "J. Smith"))
                    .isInstanceOf(CommentValidationException.class);
        }

        @Test
        void should_accept_comment_text_of_exactly_1000_characters() {
            String exactly1000 = "a".repeat(1000);
            when(eventPort.addComment(any(), any())).thenReturn(true);

            assertThatNoException().isThrownBy(() -> eventService.addComment("evt-001", exactly1000, "J. Smith"));
        }

        @Test
        void should_generate_server_side_id_and_created_at_on_successful_comment() {
            when(eventPort.addComment(any(), any())).thenReturn(true);

            AddCommentResponse response = eventService.addComment("evt-001", "Reviewed — crew received a TCAS RA", "J. Smith");

            assertThat(response.id()).isNotNull();
            assertThat(response.text()).isEqualTo("Reviewed — crew received a TCAS RA");
            assertThat(response.createdAt()).isNotNull();
        }

        @Test
        void should_return_404_when_event_not_found() {
            when(eventPort.addComment(any(), any())).thenReturn(false);

            assertThatThrownBy(() -> eventService.addComment("unknown-id", "Valid text", "J. Smith"))
                    .isInstanceOf(EventNotFoundException.class);
        }

        @Test
        void should_reject_null_author() {
            assertThatThrownBy(() -> eventService.addComment("evt-001", "Valid text", null))
                    .isInstanceOf(CommentValidationException.class);
        }

        @Test
        void should_reject_blank_author() {
            assertThatThrownBy(() -> eventService.addComment("evt-001", "Valid text", "   "))
                    .isInstanceOf(CommentValidationException.class);
        }

        @Test
        void should_reject_author_exceeding_100_characters() {
            String tooLong = "a".repeat(101);
            assertThatThrownBy(() -> eventService.addComment("evt-001", "Valid text", tooLong))
                    .isInstanceOf(CommentValidationException.class);
        }

        @Test
        void should_accept_author_of_exactly_100_characters() {
            String exactly100 = "a".repeat(100);
            when(eventPort.addComment(any(), any())).thenReturn(true);
            assertThatNoException().isThrownBy(() -> eventService.addComment("evt-001", "Valid text", exactly100));
        }

        @Test
        void should_preserve_author_in_response() {
            when(eventPort.addComment(any(), any())).thenReturn(true);
            AddCommentResponse response = eventService.addComment("evt-001", "Valid text", "J. Smith");
            assertThat(response.author()).isEqualTo("J. Smith");
        }
    }
}
