package io.webcontify.identity.model;

public enum UserStatus {
    INVITED,
    ACTIVE,
    DEACTIVATED;

    public boolean canTransitionTo(UserStatus target) {
        return switch (this) {
            case INVITED -> target == ACTIVE || target == DEACTIVATED;
            case ACTIVE -> target == DEACTIVATED;
            case DEACTIVATED -> target == ACTIVE;
        };
    }
}
