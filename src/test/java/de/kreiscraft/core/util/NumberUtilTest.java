package de.kreiscraft.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NumberUtilTest {

    @Test
    void parseInt_valid() {
        assertThat(NumberUtil.parseInt("42")).hasValue(42);
        assertThat(NumberUtil.parseInt("-7")).hasValue(-7);
    }

    @Test
    void parseInt_invalid() {
        assertThat(NumberUtil.parseInt("banana")).isEmpty();
        assertThat(NumberUtil.parseInt("")).isEmpty();
        assertThat(NumberUtil.parseInt(null)).isEmpty();
    }

    @Test
    void parseDouble_valid() {
        assertThat(NumberUtil.parseDouble("3.14")).hasValue(3.14);
    }

    @Test
    void parseDouble_invalid() {
        assertThat(NumberUtil.parseDouble("pi")).isEmpty();
    }
}
