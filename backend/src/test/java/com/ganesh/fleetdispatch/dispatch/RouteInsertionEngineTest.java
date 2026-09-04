package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.graph.NodeId;
import com.ganesh.fleetdispatch.graph.Route;
import com.ganesh.fleetdispatch.routing.Router;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteInsertionEngineTest {

    @Test
    void insertsSecondOrderWithoutMeaningfullyDelayingExistingDelivery() {
        Router router = weightedRouter(Map.ofEntries(
                Map.entry(key(1, 2), 10.0),
                Map.entry(key(2, 3), 10.0),
                Map.entry(key(1, 4), 4.0),
                Map.entry(key(4, 2), 4.0),
                Map.entry(key(4, 5), 5.0),
                Map.entry(key(5, 3), 5.0),
                Map.entry(key(2, 5), 6.0),
                Map.entry(key(5, 2), 6.0),
                Map.entry(key(1, 5), 9.0),
                Map.entry(key(5, 4), 3.0),
                Map.entry(key(4, 3), 7.0)
        ));
        RouteInsertionEngine engine = new RouteInsertionEngine(router, 5.0, 100.0);

        Order first = order(100, 2, 3);
        Order second = order(101, 4, 5);
        DriverRoutePlan plan = DriverRoutePlan.single(first);

        var result = engine.evaluate(new NodeId(1), plan, second);

        assertTrue(result.isPresent());
        RouteInsertionResult insertion = result.get();
        assertEquals(2, insertion.plan().activeDeliveryCount());
        assertTrue(insertion.incrementalTravelTimeSeconds() >= 0.0);
        assertTrue(insertion.maxExistingDeliveryEtaIncreaseSeconds() <= 5.0);
        assertTrue(insertion.plan().stops().stream()
                .map(RouteStop::orderId)
                .filter(id -> id == second.id())
                .count() == 2);
    }

    @Test
    void refusesInsertionAfterThreeActiveDeliveries() {
        Router router = weightedRouter(Map.of());
        RouteInsertionEngine engine = new RouteInsertionEngine(router, 300.0, 1_800.0);
        DriverRoutePlan plan = new DriverRoutePlan(
                List.of(order(1, 1, 2), order(2, 2, 3), order(3, 3, 4)),
                List.of(
                        new RouteStop(1, RouteStopType.PICKUP, new NodeId(1)),
                        new RouteStop(1, RouteStopType.DROPOFF, new NodeId(2)),
                        new RouteStop(2, RouteStopType.PICKUP, new NodeId(2)),
                        new RouteStop(2, RouteStopType.DROPOFF, new NodeId(3)),
                        new RouteStop(3, RouteStopType.PICKUP, new NodeId(3)),
                        new RouteStop(3, RouteStopType.DROPOFF, new NodeId(4))));

        assertFalse(engine.evaluate(new NodeId(1), plan, order(4, 4, 5)).isPresent());
    }

    @Test
    void tighterNewOrderSlaRejectsAnOtherwiseValidInsertion() {
        Router router = weightedRouter(Map.ofEntries(
                Map.entry(key(1, 2), 5.0),
                Map.entry(key(2, 3), 5.0),
                Map.entry(key(1, 4), 4.0),
                Map.entry(key(4, 5), 4.0),
                Map.entry(key(5, 2), 4.0),
                Map.entry(key(2, 5), 4.0),
                Map.entry(key(5, 3), 4.0)
        ));
        RouteInsertionEngine engine = new RouteInsertionEngine(router, 100.0, 100.0);

        Order first = order(200, 2, 3);
        Order second = order(201, 4, 5);
        DriverRoutePlan plan = DriverRoutePlan.single(first);

        assertTrue(engine.evaluate(new NodeId(1), plan, second).isPresent());
        assertFalse(engine.evaluate(
                new NodeId(1),
                plan,
                second,
                new DeliveryConstraints(100.0, 6.0)).isPresent());
    }

    @Test
    void tighterExistingOrderSlaRejectsRouteThatDelaysCurrentDeliveryTooMuch() {
        Router router = weightedRouter(Map.ofEntries(
                Map.entry(key(1, 2), 5.0),
                Map.entry(key(2, 3), 5.0),
                Map.entry(key(1, 4), 4.0),
                Map.entry(key(4, 2), 4.0),
                Map.entry(key(4, 5), 4.0),
                Map.entry(key(5, 3), 4.0),
                Map.entry(key(2, 5), 20.0),
                Map.entry(key(5, 2), 20.0)
        ));
        RouteInsertionEngine engine = new RouteInsertionEngine(router, 100.0, 100.0);

        Order first = order(300, 2, 3);
        Order second = order(301, 4, 5);
        DriverRoutePlan plan = DriverRoutePlan.single(first);

        assertTrue(engine.evaluate(new NodeId(1), plan, second).isPresent());
        assertFalse(engine.evaluate(
                new NodeId(1),
                plan,
                second,
                new DeliveryConstraints(2.0, 100.0)).isPresent());
    }

    @Test
    void explicitSlaCanBeLooserThanEngineDefault() {
        Router router = weightedRouter(Map.ofEntries(
                Map.entry(key(1, 2), 5.0),
                Map.entry(key(2, 3), 5.0),
                Map.entry(key(1, 4), 4.0),
                Map.entry(key(4, 2), 4.0),
                Map.entry(key(4, 5), 4.0),
                Map.entry(key(5, 3), 4.0),
                Map.entry(key(2, 5), 20.0),
                Map.entry(key(5, 2), 20.0)
        ));
        RouteInsertionEngine engine = new RouteInsertionEngine(router, 2.0, 10.0);

        Order first = order(400, 2, 3);
        Order second = order(401, 4, 5);
        DriverRoutePlan plan = DriverRoutePlan.single(first);

        assertFalse(engine.evaluate(new NodeId(1), plan, second).isPresent());
        assertTrue(engine.evaluate(
                new NodeId(1),
                plan,
                second,
                new DeliveryConstraints(100.0, 100.0)).isPresent());
    }

    @Test
    void doesNotChangeTheBestInsertionWhenSlaAllowsAllCandidates() {
        Router router = weightedRouter(Map.ofEntries(
                Map.entry(key(1, 2), 10.0),
                Map.entry(key(2, 3), 10.0),
                Map.entry(key(1, 4), 4.0),
                Map.entry(key(4, 2), 4.0),
                Map.entry(key(4, 5), 5.0),
                Map.entry(key(5, 3), 5.0),
                Map.entry(key(2, 5), 6.0),
                Map.entry(key(5, 2), 6.0),
                Map.entry(key(1, 5), 9.0),
                Map.entry(key(5, 4), 3.0),
                Map.entry(key(4, 3), 7.0)
        ));
        RouteInsertionEngine engine = new RouteInsertionEngine(router, 5.0, 100.0);

        Order first = order(500, 2, 3);
        Order second = order(501, 4, 5);
        DriverRoutePlan plan = DriverRoutePlan.single(first);

        RouteInsertionResult defaultResult = engine.evaluate(new NodeId(1), plan, second).orElseThrow();
        RouteInsertionResult explicitResult = engine.evaluate(
                new NodeId(1),
                plan,
                second,
                new DeliveryConstraints(100.0, 1_000.0)).orElseThrow();

        assertEquals(defaultResult.route().nodes(), explicitResult.route().nodes());
        assertEquals(defaultResult.incrementalTravelTimeSeconds(),
                explicitResult.incrementalTravelTimeSeconds());
        assertEquals(defaultResult.incrementalDistanceMeters(),
                explicitResult.incrementalDistanceMeters());
    }

    private static Order order(long id, long pickup, long dropoff) {
        return new Order(id, new NodeId(pickup), new NodeId(dropoff), id, OrderStatus.ASSIGNED);
    }

    private static String key(long source, long target) {
        return source + ":" + target;
    }

    private static Router weightedRouter(Map<String, Double> weights) {
        return (source, target) -> {
            if (source.equals(target)) {
                return new Route(List.of(source), 0.0, 0.0);
            }
            double value = weights.getOrDefault(key(source.value(), target.value()), 100.0);
            return new Route(List.of(source, target), value, value);
        };
    }
}
