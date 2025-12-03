package ch.sbb.polarion.extension.docx_exporter.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UrlUtilsTest {

    @Test
    void testEncodeInvalidUriCharactersWithSpaces() {
        assertEquals("hello%20world", UrlUtils.encodeInvalidUriCharacters("hello world"));
        assertEquals("/path/to/file%20name", UrlUtils.encodeInvalidUriCharacters("/path/to/file name"));
    }

    @Test
    void testEncodeInvalidUriCharactersPreservesAlreadyEncoded() {
        assertEquals("already%20encoded", UrlUtils.encodeInvalidUriCharacters("already%20encoded"));
        assertEquals("path%2Fwith%2Fencoded", UrlUtils.encodeInvalidUriCharacters("path%2Fwith%2Fencoded"));
    }

    @Test
    void testEncodeInvalidUriCharactersWithEmptyAndNull() {
        assertEquals("", UrlUtils.encodeInvalidUriCharacters(""));
        assertNull(UrlUtils.encodeInvalidUriCharacters(null));
    }

    @Test
    void testEncodeInvalidUriCharactersPreservesValidCharacters() {
        assertEquals("/normal/path?query=value#fragment", UrlUtils.encodeInvalidUriCharacters("/normal/path?query=value#fragment"));
        assertEquals("http://example.com:8080/path", UrlUtils.encodeInvalidUriCharacters("http://example.com:8080/path"));
    }

    @Test
    void testEncodeInvalidUriCharactersWithPolarionUrls() {
        assertEquals(
                "/polarion/#/project/MyProject/wiki/Common/My%20Document%20Title",
                UrlUtils.encodeInvalidUriCharacters("/polarion/#/project/MyProject/wiki/Common/My Document Title")
        );
        assertEquals(
                "/polarion/wiki/xwiki/edit?%20parent=project/page/01%20Concept",
                UrlUtils.encodeInvalidUriCharacters("/polarion/wiki/xwiki/edit? parent=project/page/01 Concept")
        );
    }
}
