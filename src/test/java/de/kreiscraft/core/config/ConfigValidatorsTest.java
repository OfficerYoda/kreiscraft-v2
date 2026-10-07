package de.kreiscraft.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import de.kreiscraft.core.config.serializers.KreiscraftSerializers;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;
import org.spongepowered.configurate.serialize.SerializationException;

class ConfigValidatorsTest {

    private enum Level {
        LOW, HIGH
    }

    private static ConfigurationNode node(Object value) {
        ConfigurationNode node =
                CommentedConfigurationNode.root(
                        ConfigurationOptions.defaults()
                                .serializers(KreiscraftSerializers.builder().build()));
        try {
            node.set(value);
        } catch (SerializationException e) {
            throw new IllegalStateException(e);
        }
        return node;
    }

    @Test
    void alwaysValid_acceptsAnything() {
        assertThat(ConfigValidators.alwaysValid().validate(node("whatever"))).isEmpty();
    }

    @Test
    void booleanValue_acceptsLiteralBoolean() {
        assertThat(ConfigValidators.booleanValue().validate(node(true))).isEmpty();
        assertThat(ConfigValidators.booleanValue().validate(node(false))).isEmpty();
    }

    @Test
    void booleanValue_rejectsCoercibleString() {
        assertThat(ConfigValidators.booleanValue().validate(node("yes"))).isPresent();
    }

    @Test
    void integerRange_acceptsWithinBounds() {
        assertThat(ConfigValidators.integerRange(1, 6).validate(node(6))).isEmpty();
        assertThat(ConfigValidators.integerRange(1, 6).validate(node(1))).isEmpty();
    }

    @Test
    void integerRange_rejectsOutOfBoundsAndFractions() {
        assertThat(ConfigValidators.integerRange(1, 6).validate(node(7))).isPresent();
        assertThat(ConfigValidators.integerRange(1, 6).validate(node(2.5))).isPresent();
        assertThat(ConfigValidators.integerRange(1, 6).validate(node("3"))).isPresent();
    }

    @Test
    void decimalRange_acceptsWithinBounds() {
        assertThat(ConfigValidators.decimalRange(0.0, 1.0).validate(node(0.5))).isEmpty();
    }

    @Test
    void decimalRange_rejectsOutOfBounds() {
        assertThat(ConfigValidators.decimalRange(0.0, 1.0).validate(node(1.5))).isPresent();
    }

    @Test
    void duration_usesRegisteredSerializer() {
        assertThat(ConfigValidators.duration().validate(node("5s"))).isEmpty();
        assertThat(ConfigValidators.duration().validate(node("not-a-duration"))).isPresent();
    }

    @Test
    void enumValue_acceptsKnownNameIgnoringCase() {
        assertThat(ConfigValidators.enumValue(Level.class).validate(node("low"))).isEmpty();
        assertThat(ConfigValidators.enumValue(Level.class).validate(node("HIGH"))).isEmpty();
    }

    @Test
    void enumValue_rejectsUnknownName() {
        assertThat(ConfigValidators.enumValue(Level.class).validate(node("MEDIUM"))).isPresent();
    }

    @Test
    void choiceValidators_exposeTheirOptionsForTheGui() {
        assertThat(((ChoiceValidator) ConfigValidators.enumValue(Level.class)).options())
                .containsExactly("LOW", "HIGH");
        assertThat(((ChoiceValidator) ConfigValidators.oneOf("de", "en")).options())
                .containsExactly("de", "en");
    }

    @Test
    void uri_acceptsUrlsAndRejectsBlanks() {
        assertThat(ConfigValidators.uri().validate(node("https://kreiscraft.de"))).isEmpty();
        assertThat(ConfigValidators.uri().validate(node("   "))).isPresent();
    }

    @Test
    void miniMessage_acceptsTagsAndRejectsMissingValue() {
        assertThat(ConfigValidators.miniMessage().validate(node("<gray>Hello</gray>"))).isEmpty();
        assertThat(ConfigValidators.miniMessage().validate(node(null))).isPresent();
    }

    @Test
    void nonEmptyString_rejectsBlank() {
        assertThat(ConfigValidators.nonEmptyString().validate(node("text"))).isEmpty();
        assertThat(ConfigValidators.nonEmptyString().validate(node("  "))).isPresent();
    }

    @Test
    void stringList_acceptsListAndRejectsScalar() {
        assertThat(ConfigValidators.stringList().validate(node(List.of("a", "b")))).isEmpty();
        assertThat(ConfigValidators.stringList().validate(node("a"))).isPresent();
    }
}
