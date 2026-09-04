package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.graph.NodeId;
import com.ganesh.fleetdispatch.graph.Route;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Creates and manages expiring driver offers with atomic lifecycle transitions. */
public final class DispatchOfferService {
    private final CandidateSelector candidateSelector;
    private final DriverStateStore driverStateStore;
    private final OrderStateStore orderStateStore;
    private final DriverRouteStore driverRouteStore;
    private final RouterAdapter routerAdapter;
    private final DispatchOfferStore offerStore;
    private final double searchRadiusMeters;
    private final int maxCandidates;
    private final long offerTtlMillis;
    private final AtomicLong offerIds = new AtomicLong(1L);
    private final ConcurrentHashMap<Long, Object> orderLocks = new ConcurrentHashMap<>();

    public DispatchOfferService(
            CandidateSelector candidateSelector,
            DriverStateStore driverStateStore,
            OrderStateStore orderStateStore,
            DriverRouteStore driverRouteStore,
            RouterAdapter routerAdapter,
            DispatchOfferStore offerStore,
            double searchRadiusMeters,
            int maxCandidates,
            long offerTtlMillis) {
        this.candidateSelector = Objects.requireNonNull(candidateSelector, "candidateSelector");
        this.driverStateStore = Objects.requireNonNull(driverStateStore, "driverStateStore");
        this.orderStateStore = Objects.requireNonNull(orderStateStore, "orderStateStore");
        this.driverRouteStore = Objects.requireNonNull(driverRouteStore, "driverRouteStore");
        this.routerAdapter = Objects.requireNonNull(routerAdapter, "routerAdapter");
        this.offerStore = Objects.requireNonNull(offerStore, "offerStore");
        if (!Double.isFinite(searchRadiusMeters) || searchRadiusMeters < 0.0) {
            throw new IllegalArgumentException("searchRadiusMeters must be finite and non-negative");
        }
        if (maxCandidates <= 0) {
            throw new IllegalArgumentException("maxCandidates must be positive");
        }
        if (offerTtlMillis <= 0) {
            throw new IllegalArgumentException("offerTtlMillis must be positive");
        }
        this.searchRadiusMeters = searchRadiusMeters;
        this.maxCandidates = maxCandidates;
        this.offerTtlMillis = offerTtlMillis;
    }

    public Optional<DispatchOffer> createOffer(Order order, long nowMillis) {
        return createOffer(order, nowMillis, Set.of());
    }

    private Optional<DispatchOffer> createOffer(
            Order order,
            long nowMillis,
            Set<Long> excludedDriverIds) {
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(excludedDriverIds, "excludedDriverIds");
        if (nowMillis < 0) {
            throw new IllegalArgumentException("nowMillis must be non-negative");
        }

        synchronized (orderLocks.computeIfAbsent(order.id(), ignored -> new Object())) {
            Optional<Order> current = orderStateStore.getOrder(order.id());
            if (current.isEmpty() || current.get().status() != OrderStatus.CREATED) {
                return Optional.empty();
            }

            List<DriverCandidate> candidates = candidateSelector
                    .select(order, searchRadiusMeters, maxCandidates);
            for (DriverCandidate candidate : candidates) {
                Driver driver = candidate.driver();
                if (excludedDriverIds.contains(driver.id())) {
                    continue;
                }

                NodeId expectedNode = driver.currentNode();
                Optional<Route> route = routerAdapter.findRoute(driver.currentNode(), order.pickupNode());
                if (route.isEmpty()) {
                    continue;
                }

                if (!driverStateStore.reserveDriver(driver.id(), expectedNode)) {
                    continue;
                }

                if (!orderStateStore.tryOffer(order.id(), driver.id())) {
                    driverStateStore.releaseDriver(driver.id(), expectedNode);
                    continue;
                }

                long offerId = offerIds.getAndIncrement();
                DispatchOffer offer = new DispatchOffer(
                        offerId,
                        order.id(),
                        driver.id(),
                        expectedNode,
                        route.get(),
                        nowMillis,
                        Math.addExact(nowMillis, offerTtlMillis),
                        DispatchOfferStatus.PENDING);
                offerStore.create(offer);
                return Optional.of(offer);
            }
            return Optional.empty();
        }
    }

    /** Re-offers an order only after a rejected or expired offer, excluding prior drivers. */
    public ReofferResult reOffer(long offerId, long nowMillis) {
        if (offerId < 0) {
            throw new IllegalArgumentException("offerId must be non-negative");
        }
        if (nowMillis < 0) {
            throw new IllegalArgumentException("nowMillis must be non-negative");
        }

        Optional<DispatchOffer> sourceOptional = offerStore.get(offerId);
        if (sourceOptional.isEmpty()) {
            return ReofferResult.notEligible();
        }

        DispatchOffer source = sourceOptional.get();
        if (source.status() != DispatchOfferStatus.REJECTED
                && source.status() != DispatchOfferStatus.EXPIRED) {
            return ReofferResult.notEligible();
        }

        synchronized (orderLocks.computeIfAbsent(source.orderId(), ignored -> new Object())) {
            Optional<Order> order = orderStateStore.getOrder(source.orderId());
            if (order.isEmpty() || order.get().status() != OrderStatus.CREATED) {
                return ReofferResult.notEligible();
            }

            if (!offerStore.getPendingOffersForOrder(source.orderId()).isEmpty()) {
                return ReofferResult.notEligible();
            }

            Set<Long> excludedDriverIds = new HashSet<>();
            for (DispatchOffer previous : offerStore.getOffersForOrder(source.orderId())) {
                excludedDriverIds.add(previous.driverId());
            }

            return createOffer(order.get(), nowMillis, excludedDriverIds)
                    .map(ReofferResult::offered)
                    .orElseGet(ReofferResult::unavailable);
        }
    }

    public boolean accept(long offerId) {
        return withOfferLock(offerId, () -> acceptLocked(offerId));
    }

    public boolean reject(long offerId) {
        return withOfferLock(offerId, () -> rejectLocked(offerId));
    }

    public boolean expire(long offerId, long nowMillis) {
        if (nowMillis < 0) {
            throw new IllegalArgumentException("nowMillis must be non-negative");
        }
        return withOfferLock(offerId, () -> expireLocked(offerId, nowMillis));
    }

    public Optional<DispatchOffer> get(long offerId) {
        return offerStore.get(offerId);
    }

    public List<DispatchOffer> pendingOffersForOrder(long orderId) {
        return offerStore.getPendingOffersForOrder(orderId);
    }

    private boolean acceptLocked(long offerId) {
        Optional<DispatchOffer> offerOptional = offerStore.get(offerId);
        if (offerOptional.isEmpty()) {
            return false;
        }
        DispatchOffer offer = offerOptional.get();
        if (offer.status() != DispatchOfferStatus.PENDING) {
            return false;
        }

        if (!offerStore.transition(offerId, DispatchOfferStatus.PENDING, DispatchOfferStatus.ACCEPTED)) {
            return false;
        }

        if (!orderStateStore.tryAcceptOffer(offer.orderId(), offer.driverId())) {
            offerStore.transition(offerId, DispatchOfferStatus.ACCEPTED, DispatchOfferStatus.CANCELLED);
            driverStateStore.releaseDriver(offer.driverId(), offer.expectedDriverNode());
            return false;
        }

        Order assignedOrder = orderStateStore.getOrder(offer.orderId()).orElseThrow();
        driverRouteStore.putPlan(offer.driverId(), DriverRoutePlan.single(assignedOrder));
        return true;
    }

    private boolean rejectLocked(long offerId) {
        Optional<DispatchOffer> offerOptional = offerStore.get(offerId);
        if (offerOptional.isEmpty()) {
            return false;
        }
        DispatchOffer offer = offerOptional.get();
        if (!offerStore.transition(offerId, DispatchOfferStatus.PENDING, DispatchOfferStatus.REJECTED)) {
            return false;
        }

        if (!orderStateStore.tryRejectOffer(offer.orderId(), offer.driverId())) {
            return false;
        }
        driverStateStore.releaseDriver(offer.driverId(), offer.expectedDriverNode());
        return true;
    }

    private boolean expireLocked(long offerId, long nowMillis) {
        Optional<DispatchOffer> offerOptional = offerStore.get(offerId);
        if (offerOptional.isEmpty()) {
            return false;
        }
        DispatchOffer offer = offerOptional.get();
        if (offer.status() != DispatchOfferStatus.PENDING || nowMillis < offer.expiresAtMillis()) {
            return false;
        }
        if (!offerStore.transition(offerId, DispatchOfferStatus.PENDING, DispatchOfferStatus.EXPIRED)) {
            return false;
        }

        if (!orderStateStore.tryExpireOffer(offer.orderId(), offer.driverId())) {
            return false;
        }
        driverStateStore.releaseDriver(offer.driverId(), offer.expectedDriverNode());
        return true;
    }

    private boolean withOfferLock(long offerId, BooleanSupplier action) {
        if (offerId < 0) {
            throw new IllegalArgumentException("offerId must be non-negative");
        }
        Optional<DispatchOffer> offer = offerStore.get(offerId);
        if (offer.isEmpty()) {
            return false;
        }
        synchronized (orderLocks.computeIfAbsent(offer.get().orderId(), ignored -> new Object())) {
            return action.getAsBoolean();
        }
    }

    @FunctionalInterface
    private interface BooleanSupplier {
        boolean getAsBoolean();
    }

    @FunctionalInterface
    public interface RouterAdapter {
        Optional<Route> findRoute(NodeId source, NodeId target);
    }
}
