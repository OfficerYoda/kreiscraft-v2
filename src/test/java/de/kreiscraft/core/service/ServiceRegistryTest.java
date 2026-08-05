package de.kreiscraft.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ServiceRegistryTest {

    interface Foo {
        String name();
    }

    @Test
    void publish_thenGet_returnsImpl() {
        ServiceRegistry r = new ServiceRegistry();
        Foo impl = () -> "hello";
        r.publish(Foo.class, impl);
        assertThat(r.get(Foo.class).name()).isEqualTo("hello");
    }

    @Test
    void get_missing_throws() {
        ServiceRegistry r = new ServiceRegistry();
        assertThatThrownBy(() -> r.get(Foo.class))
                .isInstanceOf(ServiceNotFoundException.class);
    }

    @Test
    void find_missing_empty() {
        ServiceRegistry r = new ServiceRegistry();
        assertThat(r.find(Foo.class)).isEmpty();
    }

    @Test
    void publish_replacesPrevious() {
        ServiceRegistry r = new ServiceRegistry();
        r.publish(Foo.class, () -> "one");
        r.publish(Foo.class, () -> "two");
        assertThat(r.get(Foo.class).name()).isEqualTo("two");
    }
}
