package de.kreiscraft.core.service;

public final class ServiceNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ServiceNotFoundException(Class<?> type) {
        super("no service registered for " + type.getName());
    }
}
