package ch.sbb.polarion.extension.docx_exporter.pandoc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StripTrailingSlashesTest {

    @ParameterizedTest
    @CsvSource(value = {
            "http://localhost:9082      | http://localhost:9082",
            "http://localhost:9082/     | http://localhost:9082",
            "http://localhost:9082///   | http://localhost:9082",
            "/                          | ''",
            "////                       | ''",
            "''                         | ''",
            "http://host/path/          | http://host/path",
    }, delimiterString = "|", ignoreLeadingAndTrailingWhitespace = true)
    void stripsTrailingSlashes(String input, String expected) {
        assertEquals(expected, BasePandocTest.stripTrailingSlashes(input));
    }

    @Test
    void leavesInnerSlashesUntouched() {
        assertEquals("http://host//path", BasePandocTest.stripTrailingSlashes("http://host//path//"));
    }

    @Test
    void isLinearOnManyTrailingSlashes() {
        String input = "x" + "/".repeat(100_000);
        assertEquals("x", BasePandocTest.stripTrailingSlashes(input));
    }
}
