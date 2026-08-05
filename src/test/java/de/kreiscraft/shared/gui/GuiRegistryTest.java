package de.kreiscraft.shared.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GuiRegistryTest {

    @Test
    void register_thenClose_closesEveryHandle() {
        GuiRegistry reg = new GuiRegistry();
        AtomicInteger closed = new AtomicInteger();
        reg.register(closed::incrementAndGet);
        reg.register(closed::incrementAndGet);
        reg.closeAll();
        assertThat(closed).hasValue(2);
        assertThat(reg.openCount()).isZero();
    }

    @Test
    void unregister_removesFromRegistry() {
        GuiRegistry reg = new GuiRegistry();
        AtomicInteger closed = new AtomicInteger();
        GuiRegistry.Handle h = closed::incrementAndGet;
        reg.register(h);
        reg.unregister(h);
        reg.closeAll();
        assertThat(closed).hasValue(0);
    }

    @Test
    void closeAll_survivesExceptions() {
        GuiRegistry reg = new GuiRegistry();
        AtomicInteger closed = new AtomicInteger();
        reg.register(
                () -> {
                    throw new RuntimeException("boom");
                });
        reg.register(closed::incrementAndGet);
        reg.closeAll();
        assertThat(closed).hasValue(1);
        assertThat(reg.openCount()).isZero();
    }
}
