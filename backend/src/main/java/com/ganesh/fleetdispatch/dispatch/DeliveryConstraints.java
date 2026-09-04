package com.ganesh.fleetdispatch.dispatch;

/** SLA constraints applied while evaluating a driver's route. */
public record DeliveryConstraints(
        double maxExistingDeliveryEtaIncreaseSeconds,
        double maxNewOrderDeliveryEtaSeconds) {
    public DeliveryConstraints {
        validate(maxExistingDeliveryEtaIncreaseSeconds, "maxExistingDeliveryEtaIncreaseSeconds");
        validate(maxNewOrderDeliveryEtaSeconds, "maxNewOrderDeliveryEtaSeconds");
    }

    private static void validate(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }
}
