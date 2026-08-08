package de.kreiscraft.core.service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ServiceRegistry {

    private final Map<Class<?>, Object> services = new ConcurrentHashMap<>();

    public <T> void publish(Class<T> type, T impl) {
        services.put(type, impl);
    }

    public <T> T get(Class<T> type) {
        Object o = services.get(type);
        if (o == null) {
            throw new ServiceNotFoundException(type);
        }
        return type.cast(o);
    }

    public <T> Optional<T> find(Class<T> type) {
        return Optional.ofNullable(services.get(type)).map(type::cast);
    }
}
