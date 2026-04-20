package io.webcontify.shared.model;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

public record ProvisioningStatus(
        List<ProvisioningStep> steps,
        String adminEmail,
        String adminFirstName,
        String adminLastName) {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    public static ProvisioningStatus initial(String adminEmail, String adminFirstName, String adminLastName) {
        return new ProvisioningStatus(
                List.of(
                        ProvisioningStep.pending(ProvisioningStepType.S3_BUCKET),
                        ProvisioningStep.pending(ProvisioningStepType.KEYCLOAK_REALM),
                        ProvisioningStep.pending(ProvisioningStepType.OPENFGA_STORE),
                        ProvisioningStep.pending(ProvisioningStepType.TENANT_SCHEMA),
                        ProvisioningStep.pending(ProvisioningStepType.INITIAL_ADMIN)),
                adminEmail,
                adminFirstName,
                adminLastName);
    }

    /**
     * Migrates a 4-step snapshot (pre-TENANT_SCHEMA) to 5-step format by inserting a completed
     * TENANT_SCHEMA step at index 3. For backward compatibility with existing tenants.
     */
    public ProvisioningStatus migrateIfNeeded() {
        if (steps.size() == 4 && steps.stream().noneMatch(s -> s.step() == ProvisioningStepType.TENANT_SCHEMA)) {
            var migrated = new java.util.ArrayList<>(steps);
            migrated.add(3, ProvisioningStep.pending(ProvisioningStepType.TENANT_SCHEMA).completed());
            return new ProvisioningStatus(migrated, adminEmail, adminFirstName, adminLastName);
        }
        return this;
    }

    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static ProvisioningStatus fromJson(String json) {
        try {
            return MAPPER.readValue(json, ProvisioningStatus.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public boolean allCompleted() {
        return steps.stream().allMatch(s -> s.status() == ProvisioningStepStatus.COMPLETED);
    }

    public boolean hasFailed() {
        return steps.stream().anyMatch(s -> s.status() == ProvisioningStepStatus.FAILED);
    }
}
