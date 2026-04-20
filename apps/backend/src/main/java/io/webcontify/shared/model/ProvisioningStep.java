package io.webcontify.shared.model;

import java.time.Instant;

public record ProvisioningStep(
        ProvisioningStepType step,
        ProvisioningStepStatus status,
        Instant completedAt,
        String error) {

    public static ProvisioningStep pending(ProvisioningStepType step) {
        return new ProvisioningStep(step, ProvisioningStepStatus.PENDING, null, null);
    }

    public ProvisioningStep inProgress() {
        return new ProvisioningStep(step, ProvisioningStepStatus.IN_PROGRESS, null, null);
    }

    public ProvisioningStep completed() {
        return new ProvisioningStep(step, ProvisioningStepStatus.COMPLETED, Instant.now(), null);
    }

    public ProvisioningStep failed(String error) {
        return new ProvisioningStep(step, ProvisioningStepStatus.FAILED, null, error);
    }
}
