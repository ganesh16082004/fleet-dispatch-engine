package com.ganesh.fleetdispatch.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/events")
@Tag(name = "Events", description = "Recent durable domain events")
public class EventController {
    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping("/recent")
    @Operation(summary = "Get recent domain events")
    public List<EventResponse> recent(
            @Parameter(description = "Maximum number of events to return", example = "25")
            @RequestParam(defaultValue = "25") int limit) {
        return eventService.recent(limit);
    }
}
