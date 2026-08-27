package com.atcsafety.restapi.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing event query endpoints to the Angular frontend.
 *
 * <p>Delegates all business logic to {@link EventService}. Responsible only for
 * HTTP parameter binding and routing — no domain decisions here.
 */
@RestController
@RequestMapping("/api/v1/events")
class EventController {

    private final EventService eventService;

    EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping("/summary")
    EventSummaryResponse getSummary() {
        return eventService.getSummary();
    }

    @GetMapping("/{id}")
    EventDetailResponse getEventDetail(@PathVariable String id) {
        return eventService.getEventDetail(id);
    }

    @PatchMapping("/{id}/status")
    ChangeStatusResponse changeStatus(@PathVariable String id, @RequestBody ChangeStatusRequest request) {
        return eventService.changeStatus(id, request.status(), request.escalationReason());
    }

    @PostMapping("/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    AddCommentResponse addComment(@PathVariable String id, @RequestBody AddCommentRequest request) {
        return eventService.addComment(id, request.text(), request.author());
    }

    /**
     * Returns a paginated list of events filtered by status.
     *
     * @param sort optional sort expression in {@code field,direction} format
     *             (e.g. {@code startedAt,asc}). Unrecognised field names produce 400.
     */
    @GetMapping
    PagedResponse<EventListItemResponse> listEvents(
            @RequestParam String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        String sortField = null;
        String sortDirection = "asc";
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",", 2);
            sortField = parts[0].trim();
            if (parts.length > 1) {
                sortDirection = parts[1].trim();
            }
        }
        return eventService.listEvents(status, page, size, sortField, sortDirection);
    }
}
