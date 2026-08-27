package com.atcsafety.restapi.application;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import com.atcsafety.restapi.domain.ReviewConflictException;
import com.atcsafety.restapi.domain.ReviewStatus;
import com.atcsafety.restapi.domain.ReviewValidationException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.mockito.Mockito.doThrow;

@WebMvcTest(EventController.class)
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventService eventService;

    @Nested
    class GetSummary {

        @Test
        void should_return_200_with_pending_and_escalated_counts() throws Exception {
            when(eventService.getSummary()).thenReturn(new EventSummaryResponse(7L, 3L));

            mockMvc.perform(get("/api/v1/events/summary"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pendingCount").value(7))
                    .andExpect(jsonPath("$.escalatedCount").value(3));
        }

        @Test
        void should_return_200_with_zero_counts_when_no_events_exist() throws Exception {
            when(eventService.getSummary()).thenReturn(new EventSummaryResponse(0L, 0L));

            mockMvc.perform(get("/api/v1/events/summary"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pendingCount").value(0))
                    .andExpect(jsonPath("$.escalatedCount").value(0));
        }
    }

    @Nested
    class ListEvents {

        @Test
        void should_return_400_for_unrecognised_sort_field() throws Exception {
            when(eventService.listEvents(any(), anyInt(), anyInt(), eq("arbitraryField"), any()))
                    .thenThrow(new InvalidSortFieldException("arbitraryField"));

            mockMvc.perform(get("/api/v1/events")
                            .param("status", "PENDING_REVIEW")
                            .param("sort", "arbitraryField,asc"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void should_return_200_with_empty_content_when_no_events_match_status() throws Exception {
            when(eventService.listEvents(any(), anyInt(), anyInt(), any(), any()))
                    .thenReturn(new PagedResponse<>(List.of(), 0L, 0, 0, 20));

            mockMvc.perform(get("/api/v1/events").param("status", "PENDING_REVIEW"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isEmpty())
                    .andExpect(jsonPath("$.totalElements").value(0))
                    .andExpect(jsonPath("$.totalPages").value(0));
        }
    }

    @Nested
    class GetEventDetail {

        @Test
        void should_return_200_with_event_detail_when_event_exists() throws Exception {
            var detail = new EventDetailResponse(
                    "evt-001", "AF3455", "BA202",
                    Instant.parse("2026-03-19T10:00:00Z"),
                    Instant.parse("2026-03-19T10:00:25Z"),
                    "PENDING_REVIEW", null, null, null,
                    2.1, 450.0, 6, 5, 3, 8,
                    List.of(new TrajectoryPositionData(1, Instant.parse("2026-03-19T10:00:00Z"), 60000.0, 100000.0, 20000.0, 48.946, 2.442, null)),
                    List.of(new TrajectoryPositionData(1, Instant.parse("2026-03-19T10:00:00Z"), 55000.0, 98000.0, 21000.0, 48.942, 2.436, null)),
                    List.of());
            when(eventService.getEventDetail("evt-001")).thenReturn(detail);

            mockMvc.perform(get("/api/v1/events/evt-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value("evt-001"))
                    .andExpect(jsonPath("$.firstAircraftCallsign").value("AF3455"))
                    .andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
                    .andExpect(jsonPath("$.minSeparationCycleIndex").value(6))
                    .andExpect(jsonPath("$.minVerticalSeparationCycleIndex").value(5))
                    .andExpect(jsonPath("$.eventStartCycle").value(3))
                    .andExpect(jsonPath("$.eventEndCycle").value(8))
                    .andExpect(jsonPath("$.trajectory1[0].radarCycle").value(1))
                    .andExpect(jsonPath("$.trajectory1[0].lat").value(48.946))
                    .andExpect(jsonPath("$.trajectory2[0].lon").value(2.436));
        }

        @Test
        void should_return_404_when_event_does_not_exist() throws Exception {
            when(eventService.getEventDetail("unknown-id"))
                    .thenThrow(new EventNotFoundException("unknown-id"));

            mockMvc.perform(get("/api/v1/events/unknown-id"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class ChangeStatus {

        @Test
        void should_return_200_with_dismissed_response_when_dismissing_pending_event() throws Exception {
            Instant dismissedAt = Instant.parse("2026-03-19T11:00:00Z");
            when(eventService.changeStatus("evt-001", "DISMISSED", null))
                    .thenReturn(new ChangeStatusResponse("evt-001", "DISMISSED", null, null, dismissedAt));

            mockMvc.perform(patch("/api/v1/events/evt-001/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"DISMISSED\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value("evt-001"))
                    .andExpect(jsonPath("$.status").value("DISMISSED"))
                    .andExpect(jsonPath("$.escalationReason").doesNotExist())
                    .andExpect(jsonPath("$.escalatedAt").doesNotExist())
                    .andExpect(jsonPath("$.dismissedAt").value("2026-03-19T11:00:00Z"));
        }

        @Test
        void should_return_200_with_escalated_response_when_escalating_pending_event() throws Exception {
            Instant escalatedAt = Instant.parse("2026-03-19T11:00:00Z");
            when(eventService.changeStatus("evt-001", "ESCALATED", "Near-miss; full investigation required"))
                    .thenReturn(new ChangeStatusResponse("evt-001", "ESCALATED",
                            "Near-miss; full investigation required", escalatedAt, null));

            mockMvc.perform(patch("/api/v1/events/evt-001/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"ESCALATED\",\"escalationReason\":\"Near-miss; full investigation required\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ESCALATED"))
                    .andExpect(jsonPath("$.escalationReason").value("Near-miss; full investigation required"))
                    .andExpect(jsonPath("$.escalatedAt").value("2026-03-19T11:00:00Z"))
                    .andExpect(jsonPath("$.dismissedAt").doesNotExist());
        }

        @Test
        void should_return_400_when_escalating_with_missing_escalation_reason() throws Exception {
            when(eventService.changeStatus("evt-001", "ESCALATED", null))
                    .thenThrow(new ReviewValidationException("Escalation requires a non-blank reason"));

            mockMvc.perform(patch("/api/v1/events/evt-001/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"ESCALATED\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void should_return_400_when_escalating_with_blank_escalation_reason() throws Exception {
            when(eventService.changeStatus("evt-001", "ESCALATED", "   "))
                    .thenThrow(new ReviewValidationException("Escalation requires a non-blank reason"));

            mockMvc.perform(patch("/api/v1/events/evt-001/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"ESCALATED\",\"escalationReason\":\"   \"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void should_return_409_when_event_is_already_dismissed() throws Exception {
            when(eventService.changeStatus("evt-001", "DISMISSED", null))
                    .thenThrow(new ReviewConflictException("evt-001", ReviewStatus.DISMISSED));

            mockMvc.perform(patch("/api/v1/events/evt-001/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"DISMISSED\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").isNotEmpty());
        }

        @Test
        void should_return_409_when_event_is_already_escalated() throws Exception {
            when(eventService.changeStatus("evt-001", "ESCALATED", "reason"))
                    .thenThrow(new ReviewConflictException("evt-001", ReviewStatus.ESCALATED));

            mockMvc.perform(patch("/api/v1/events/evt-001/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"ESCALATED\",\"escalationReason\":\"reason\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").isNotEmpty());
        }

        @Test
        void should_return_404_when_event_does_not_exist() throws Exception {
            when(eventService.changeStatus("unknown-id", "DISMISSED", null))
                    .thenThrow(new EventNotFoundException("unknown-id"));

            mockMvc.perform(patch("/api/v1/events/unknown-id/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"DISMISSED\"}"))
                    .andExpect(status().isNotFound());
        }

        @Test
        void should_return_400_when_request_body_is_absent() throws Exception {
            mockMvc.perform(patch("/api/v1/events/evt-001/status")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class AddComment {

        @Test
        void should_return_201_with_comment_on_successful_add() throws Exception {
            Instant createdAt = Instant.parse("2026-03-19T10:30:00Z");
            when(eventService.addComment("evt-001", "Reviewed — crew received a TCAS RA", "J. Smith"))
                    .thenReturn(new AddCommentResponse("uuid-123", "Reviewed — crew received a TCAS RA", "J. Smith", createdAt));

            mockMvc.perform(post("/api/v1/events/evt-001/comments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"text\":\"Reviewed \u2014 crew received a TCAS RA\",\"author\":\"J. Smith\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value("uuid-123"))
                    .andExpect(jsonPath("$.text").value("Reviewed \u2014 crew received a TCAS RA"))
                    .andExpect(jsonPath("$.author").value("J. Smith"))
                    .andExpect(jsonPath("$.createdAt").value("2026-03-19T10:30:00Z"));
        }

        @Test
        void should_return_400_when_comment_fails_validation() throws Exception {
            when(eventService.addComment(any(), eq("   "), any()))
                    .thenThrow(new CommentValidationException("Comment text must not be blank"));

            mockMvc.perform(post("/api/v1/events/evt-001/comments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"text\":\"   \"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void should_return_400_when_text_is_null_in_body() throws Exception {
            when(eventService.addComment(any(), isNull(), any()))
                    .thenThrow(new CommentValidationException("Comment text must not be null or blank"));

            mockMvc.perform(post("/api/v1/events/evt-001/comments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"text\":null}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void should_return_400_when_request_body_is_absent() throws Exception {
            mockMvc.perform(post("/api/v1/events/evt-001/comments")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void should_return_404_when_event_does_not_exist() throws Exception {
            when(eventService.addComment(eq("unknown-id"), any(), any()))
                    .thenThrow(new EventNotFoundException("unknown-id"));

            mockMvc.perform(post("/api/v1/events/unknown-id/comments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"text\":\"Valid comment\"}"))
                    .andExpect(status().isNotFound());
        }

        @Test
        void should_return_400_when_author_fails_validation() throws Exception {
            when(eventService.addComment(any(), any(), eq(null)))
                    .thenThrow(new CommentValidationException("Comment author must not be null or blank"));

            mockMvc.perform(post("/api/v1/events/evt-001/comments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"text\":\"Valid text\",\"author\":null}"))
                    .andExpect(status().isBadRequest());
        }
    }
}
