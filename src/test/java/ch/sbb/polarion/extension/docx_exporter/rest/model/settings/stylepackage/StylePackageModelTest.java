package ch.sbb.polarion.extension.docx_exporter.rest.model.settings.stylepackage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static ch.sbb.polarion.extension.generic.settings.NamedSettings.DEFAULT_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StylePackageModelTest {

    private static final String CUSTOM_NAME = "Custom";

    private static Stream<Arguments> weights() {
        return Stream.of(
                Arguments.of(CUSTOM_NAME, null, StylePackageModel.DEFAULT_WEIGHT),
                Arguments.of(CUSTOM_NAME, "42.5", 42.5f),
                Arguments.of(CUSTOM_NAME, " 7 ", 7f),
                Arguments.of(CUSTOM_NAME, "not a number", StylePackageModel.DEFAULT_WEIGHT),
                Arguments.of(CUSTOM_NAME, "", StylePackageModel.DEFAULT_WEIGHT),
                Arguments.of(CUSTOM_NAME, "   ", StylePackageModel.DEFAULT_WEIGHT),
                Arguments.of(DEFAULT_NAME, null, StylePackageModel.DEFAULT_INITIAL_WEIGHT),
                Arguments.of(DEFAULT_NAME, "42.5", 42.5f),
                Arguments.of(DEFAULT_NAME, "not a number", StylePackageModel.DEFAULT_INITIAL_WEIGHT),
                Arguments.of(DEFAULT_NAME, "", StylePackageModel.DEFAULT_INITIAL_WEIGHT),
                Arguments.of(DEFAULT_NAME, "   ", StylePackageModel.DEFAULT_INITIAL_WEIGHT)
        );
    }

    @ParameterizedTest
    @MethodSource("weights")
    void deserializesWeight(String packageName, String storedWeight, Float expectedWeight) {
        String content = entry("NAME", packageName) + (storedWeight == null ? "" : entry("WEIGHT", storedWeight));
        StylePackageModel model = new StylePackageModel();

        model.deserialize(content);

        assertEquals(packageName, model.getName());
        assertEquals(expectedWeight, model.getWeight());
    }

    @Test
    void keepsEmbedSvg() {
        StylePackageModel model = new StylePackageModel();
        model.setEmbedSvg(true);
        StylePackageModel restored = new StylePackageModel();

        restored.deserialize(model.serialize());

        assertTrue(restored.isEmbedSvg());
    }

    @Test
    void leavesSvgOutOfAPackageSavedBeforeTheOption() {
        StylePackageModel model = new StylePackageModel();

        model.deserialize(entry("NAME", CUSTOM_NAME));

        assertFalse(model.isEmbedSvg());
    }

    private static String entry(String entryName, String value) {
        return String.format("-----BEGIN %2$s-----%1$s%3$s%1$s-----END %2$s-----%1$s",
                System.lineSeparator(), entryName, value);
    }
}
