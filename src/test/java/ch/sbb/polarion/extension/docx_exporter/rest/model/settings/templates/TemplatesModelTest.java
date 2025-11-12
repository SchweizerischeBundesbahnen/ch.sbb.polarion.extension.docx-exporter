package ch.sbb.polarion.extension.docx_exporter.rest.model.settings.templates;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class TemplatesModelTest {

    private static Stream<Arguments> testValuesForTemplates() {
        byte[] sampleTemplate1 = "Sample template content".getBytes(StandardCharsets.UTF_8);
        byte[] sampleTemplate2 = "Another template with special chars: üäö".getBytes(StandardCharsets.UTF_8);
        byte[] emptyTemplate = new byte[0];

        return Stream.of(
                Arguments.of(null, null),
                Arguments.of("", null),
                Arguments.of("some badly formatted string", null),
                Arguments.of(String.format("ok file" +
                                "-----BEGIN BUNDLE TIMESTAMP-----%1$s" +
                                "12345%1$s" +
                                "-----END BUNDLE TIMESTAMP-----%1$s" +
                                "-----BEGIN TEMPLATE-----%1$s" +
                                "%2$s%1$s" +
                                "-----END TEMPLATE-----%1$s",
                        System.lineSeparator(),
                        Base64.getEncoder().encodeToString(sampleTemplate1)),
                        sampleTemplate1),
                Arguments.of(String.format("template with special characters" +
                                "-----BEGIN TEMPLATE-----%1$s" +
                                "%2$s%1$s" +
                                "-----END TEMPLATE-----%1$s",
                        System.lineSeparator(),
                        Base64.getEncoder().encodeToString(sampleTemplate2)),
                        sampleTemplate2),
                Arguments.of(String.format("empty template" +
                                "-----BEGIN TEMPLATE-----%1$s" +
                                "%2$s%1$s" +
                                "-----END TEMPLATE-----%1$s",
                        System.lineSeparator(),
                        Base64.getEncoder().encodeToString(emptyTemplate)),
                        emptyTemplate),
                Arguments.of(String.format("keep first duplicated entry" +
                                "-----BEGIN BUNDLE TIMESTAMP-----%1$s" +
                                "ts1%1$s" +
                                "-----END BUNDLE TIMESTAMP-----%1$s" +
                                "-----BEGIN BUNDLE TIMESTAMP-----%1$s" +
                                "ts2%1$s" +
                                "-----END BUNDLE TIMESTAMP-----%1$s" +
                                "-----BEGIN TEMPLATE-----%1$s" +
                                "%2$s%1$s" +
                                "-----END TEMPLATE-----%1$s" +
                                "-----BEGIN TEMPLATE-----%1$s" +
                                "%3$s%1$s" +
                                "-----END TEMPLATE-----%1$s",
                        System.lineSeparator(),
                        Base64.getEncoder().encodeToString(sampleTemplate1),
                        Base64.getEncoder().encodeToString(sampleTemplate2)),
                        sampleTemplate1)
        );
    }

    @ParameterizedTest
    @MethodSource("testValuesForTemplates")
    void getProperExpectedResults(String locationContent, byte[] expectedTemplate) {
        final TemplatesModel model = new TemplatesModel();
        model.deserialize(locationContent);

        if (expectedTemplate == null) {
            assertNull(model.getTemplate());
        } else {
            assertArrayEquals(expectedTemplate, model.getTemplate());
        }
    }
}
