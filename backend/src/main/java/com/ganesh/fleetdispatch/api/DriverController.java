package com.ganesh.fleetdispatch.api;

import com.ganesh.fleetdispatch.persistence.DriverDocument;
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

@RestController
@RequestMapping("/api/v1/drivers")
public class DriverController {
    private final DriverService driverService;

    public DriverController(DriverService driverService) {
        this.driverService = driverService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DriverDocument create(@RequestBody DriverRequest request) {
        return driverService.create(request);
    }

    @GetMapping
    public List<DriverDocument> findAll() {
        return driverService.findAll();
    }

    @GetMapping("/{id}")
    public DriverDocument findById(@PathVariable long id) {
        return driverService.findById(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        driverService.delete(id);
    }
}
