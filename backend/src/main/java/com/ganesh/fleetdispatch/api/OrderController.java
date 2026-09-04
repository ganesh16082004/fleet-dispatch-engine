package com.ganesh.fleetdispatch.api;

import com.ganesh.fleetdispatch.persistence.OrderDocument;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders", description = "Order lifecycle and dispatch operations")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an order")
    public OrderResponse create(@RequestBody OrderRequest request) {
        return orderService.create(request);
    }

    @GetMapping
    @Operation(summary = "List orders")
    public List<OrderResponse> findAll() {
        return orderService.findAll().stream().map(OrderResponse::from).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an order")
    public OrderResponse findById(@PathVariable long id) {
        return OrderResponse.from(orderService.findById(id));
    }

    @PostMapping("/{id}/dispatch")
    @Operation(summary = "Dispatch an order")
    public OrderResponse dispatch(@PathVariable long id) {
        return orderService.dispatch(id);
    }

    @PostMapping("/{id}/pickup")
    @Operation(summary = "Mark an order as picked up")
    public OrderResponse pickup(@PathVariable long id) {
        return orderService.pickup(id);
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "Complete an order")
    public OrderResponse complete(@PathVariable long id) {
        return orderService.complete(id);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel an order")
    public OrderResponse cancel(@PathVariable long id) {
        return orderService.cancel(id);
    }
}
