package ch.sbb.polarion.extension.docx_exporter.pandoc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReferenceFileNamesTest {

    @ParameterizedTest
    @CsvSource(value = {
            "amd64   | amd64",
            "x86_64  | amd64",
            "aarch64 | aarch64",
            "arm64   | aarch64",
            "ppc64le | ppc64le",
    }, delimiterString = "|", ignoreLeadingAndTrailingWhitespace = true)
    void namesTheArchitectureAsTheJdkOnLinux(String osArch, String expected) {
        assertEquals(expected, BasePandocTest.architecture(osArch));
    }

    @Test
    void triesTheReferenceOfTheArchitectureFirst() {
        assertEquals(List.of("page_0.amd64.png", "page_0.png"), BasePandocTest.referenceFileNames("page_0", "x86_64"));
    }
}
