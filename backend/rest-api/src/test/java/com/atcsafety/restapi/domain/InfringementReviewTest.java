package com.atcsafety.restapi.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InfringementReviewTest {

    @Nested
    class TransitionTo {

        @Test
        void should_reject_escalation_reason_exceeding_1000_characters() {
            var review = InfringementReview.pendingReview("event-001");
            String tooLong = "a".repeat(1001);

            assertThatThrownBy(() -> review.transitionTo(ReviewStatus.ESCALATED, tooLong))
                    .isInstanceOf(ReviewValidationException.class);
        }

        @Test
        void should_accept_escalation_reason_of_exactly_1000_characters() {
            var review = InfringementReview.pendingReview("event-001");
            String exactly1000 = "a".repeat(1000);

            review.transitionTo(ReviewStatus.ESCALATED, exactly1000);

            assertThat(review.getStatus()).isEqualTo(ReviewStatus.ESCALATED);
            assertThat(review.getEscalationReason()).isEqualTo(exactly1000);
        }
    }

    @Nested
    class TransitionToDismissed {

        @Test
        void should_set_status_to_dismissed_when_pending_review_event_is_dismissed() {
            var review = InfringementReview.pendingReview("event-001");

            review.transitionTo(ReviewStatus.DISMISSED, null);

            assertThat(review.getStatus()).isEqualTo(ReviewStatus.DISMISSED);
        }

        @Test
        void should_set_dismissed_at_to_current_utc_when_dismissed() {
            var before = Instant.now();
            var review = InfringementReview.pendingReview("event-001");

            review.transitionTo(ReviewStatus.DISMISSED, null);

            assertThat(review.getDismissedAt()).isAfterOrEqualTo(before);
        }

        @Test
        void should_leave_escalation_fields_null_when_dismissed() {
            var review = InfringementReview.pendingReview("event-001");

            review.transitionTo(ReviewStatus.DISMISSED, null);

            assertThat(review.getEscalationReason()).isNull();
            assertThat(review.getEscalatedAt()).isNull();
        }
    }

    @Nested
    class TransitionToEscalated {

        @Test
        void should_set_status_to_escalated_when_escalated_with_reason() {
            var review = InfringementReview.pendingReview("event-001");

            review.transitionTo(ReviewStatus.ESCALATED, "aircraft came within 2 NM at FL290");

            assertThat(review.getStatus()).isEqualTo(ReviewStatus.ESCALATED);
        }

        @Test
        void should_set_escalation_reason_when_escalated() {
            var review = InfringementReview.pendingReview("event-001");

            review.transitionTo(ReviewStatus.ESCALATED, "aircraft came within 2 NM at FL290");

            assertThat(review.getEscalationReason()).isEqualTo("aircraft came within 2 NM at FL290");
        }

        @Test
        void should_set_escalated_at_to_current_utc_when_escalated() {
            var before = Instant.now();
            var review = InfringementReview.pendingReview("event-001");

            review.transitionTo(ReviewStatus.ESCALATED, "aircraft came within 2 NM at FL290");

            assertThat(review.getEscalatedAt()).isAfterOrEqualTo(before);
        }

        @Test
        void should_throw_validation_exception_when_escalation_reason_is_null() {
            var review = InfringementReview.pendingReview("event-001");

            assertThatThrownBy(() -> review.transitionTo(ReviewStatus.ESCALATED, null))
                    .isInstanceOf(ReviewValidationException.class);
        }

        @Test
        void should_throw_validation_exception_when_escalation_reason_is_blank() {
            var review = InfringementReview.pendingReview("event-001");

            assertThatThrownBy(() -> review.transitionTo(ReviewStatus.ESCALATED, "   "))
                    .isInstanceOf(ReviewValidationException.class);
        }
    }

    @Nested
    class Reconstitute {

        @Test
        void should_create_review_with_given_status_when_reconstituting_from_persistence() {
            var review = InfringementReview.reconstitute("event-001", ReviewStatus.DISMISSED);

            assertThat(review.getId()).isEqualTo("event-001");
            assertThat(review.getStatus()).isEqualTo(ReviewStatus.DISMISSED);
        }

        @Test
        void should_throw_conflict_exception_when_transitioning_from_reconstituted_dismissed_state() {
            var review = InfringementReview.reconstitute("event-001", ReviewStatus.DISMISSED);

            assertThatThrownBy(() -> review.transitionTo(ReviewStatus.DISMISSED, null))
                    .isInstanceOf(ReviewConflictException.class);
        }

        @Test
        void should_throw_conflict_exception_when_transitioning_from_reconstituted_escalated_state() {
            var review = InfringementReview.reconstitute("event-001", ReviewStatus.ESCALATED);

            assertThatThrownBy(() -> review.transitionTo(ReviewStatus.ESCALATED, "reason"))
                    .isInstanceOf(ReviewConflictException.class);
        }
    }

    @Nested
    class ConflictOnTerminalState {

        @Test
        void should_throw_conflict_exception_when_dismissed_event_is_dismissed_again() {
            var review = InfringementReview.pendingReview("event-001");
            review.transitionTo(ReviewStatus.DISMISSED, null);

            assertThatThrownBy(() -> review.transitionTo(ReviewStatus.DISMISSED, null))
                    .isInstanceOf(ReviewConflictException.class);
        }

        @Test
        void should_throw_conflict_exception_when_escalated_event_is_escalated_again() {
            var review = InfringementReview.pendingReview("event-001");
            review.transitionTo(ReviewStatus.ESCALATED, "initial reason");

            assertThatThrownBy(() -> review.transitionTo(ReviewStatus.ESCALATED, "second reason"))
                    .isInstanceOf(ReviewConflictException.class);
        }

        @Test
        void should_throw_conflict_exception_when_escalated_event_is_dismissed() {
            var review = InfringementReview.pendingReview("event-001");
            review.transitionTo(ReviewStatus.ESCALATED, "initial reason");

            assertThatThrownBy(() -> review.transitionTo(ReviewStatus.DISMISSED, null))
                    .isInstanceOf(ReviewConflictException.class);
        }
    }
}
