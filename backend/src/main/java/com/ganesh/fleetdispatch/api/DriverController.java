package com.ganesh.fleetdispatch.api;

import com.ganesh.fleetdispatch.persistence.DriverDocument;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/drivers")
@Tag(name = "Drivers", description = "Driver registration and live fleet state")
public class DriverController {
    private final DriverService driverService;

    public DriverController(DriverService driverService) {
        this.driverService = driverService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register or reconnect a driver")
    public DriverResponse create(@RequestBody DriverRequest request) {
        return DriverResponse.from(driverService.create(request));
    }

    @GetMapping
    @Operation(summary = "List drivers")
    public List<DriverResponse> findAll() {
        return driverService.findAll().stream().map(DriverResponse::from).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a driver")
    public DriverResponse findById(@PathVariable long id) {
        return DriverResponse.from(driverService.findById(id));
    }

    @GetMapping("/{id}/location")
    @Operation(summary = "Get live driver location")
    public Map<String, Object> getLiveLocation(@PathVariable long id) {
        return driverService.getLiveLocation(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a driver")
    public void delete(@PathVariable long id) {
        driverService.delete(id);
    }
}
