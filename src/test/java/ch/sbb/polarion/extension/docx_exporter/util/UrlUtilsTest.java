package ch.sbb.polarion.extension.docx_exporter.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UrlUtilsTest {

    @Test
    void testNormalizeUrlWithSpaces() {
        assertEquals("hello%20world", UrlUtils.normalizeUrl("hello world"));
        assertEquals("/path/to/file%20name", UrlUtils.normalizeUrl("/path/to/file name"));
    }

    @Test
    void testNormalizeUrlPreservesAlreadyEncoded() {
        assertEquals("already%20encoded", UrlUtils.normalizeUrl("already%20encoded"));
        assertEquals("path%2Fwith%2Fencoded", UrlUtils.normalizeUrl("path%2Fwith%2Fencoded"));
    }

    @Test
    void testNormalizeUrlWithEmptyAndNull() {
        assertEquals("", UrlUtils.normalizeUrl(""));
        assertNull(UrlUtils.normalizeUrl(null));
    }

    @Test
    void testNormalizeUrlPreservesValidCharacters() {
        assertEquals("/normal/path?query=value#fragment", UrlUtils.normalizeUrl("/normal/path?query=value#fragment"));
        assertEquals("http://example.com:8080/path", UrlUtils.normalizeUrl("http://example.com:8080/path"));
    }

    @Test
    void testNormalizeUrlWithPolarionUrls() {
        assertEquals(
                "/polarion/#/project/MyProject/wiki/Common/My%20Document%20Title",
                UrlUtils.normalizeUrl("/polarion/#/project/MyProject/wiki/Common/My Document Title")
        );
        assertEquals(
                "/polarion/wiki/xwiki/edit?%20parent=project/page/01%20Concept",
                UrlUtils.normalizeUrl("/polarion/wiki/xwiki/edit? parent=project/page/01 Concept")
        );
    }
}
