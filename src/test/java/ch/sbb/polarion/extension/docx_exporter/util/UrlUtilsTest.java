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

    @Test
    void testNormalizeUrlEncodesOtherInvalidCharacters() {
        // Characters like < > { } | \ ^ ` should be encoded
        assertEquals("path%3Cwith%3Eangles", UrlUtils.normalizeUrl("path<with>angles"));
        assertEquals("path%7Bwith%7Dbraces", UrlUtils.normalizeUrl("path{with}braces"));
        assertEquals("path%7Cwith%7Cpipes", UrlUtils.normalizeUrl("path|with|pipes"));
        assertEquals("path%5Cwith%5Cbackslash", UrlUtils.normalizeUrl("path\\with\\backslash"));
        assertEquals("path%5Ewith%5Ecaret", UrlUtils.normalizeUrl("path^with^caret"));
        assertEquals("path%60with%60backtick", UrlUtils.normalizeUrl("path`with`backtick"));
    }

    @Test
    void testNormalizeUrlWithPercentNotFollowedByHex() {
        // % is a valid URI character and is preserved as-is when not part of encoded sequence
        // % at end of string - preserved (not enough characters for encoded sequence check)
        assertEquals("%", UrlUtils.normalizeUrl("%"));
        assertEquals("path%", UrlUtils.normalizeUrl("path%"));
        // % followed by only one character - preserved
        assertEquals("path%2", UrlUtils.normalizeUrl("path%2"));
        // % followed by non-hex characters - preserved (G and X are not hex digits)
        assertEquals("path%GH", UrlUtils.normalizeUrl("path%GH"));
        assertEquals("path%XY", UrlUtils.normalizeUrl("path%XY"));
    }

    @Test
    void testNormalizeUrlPreservesAllValidUriCharacters() {
        // Unreserved characters: - _ . ~
        assertEquals("a-b_c.d~e", UrlUtils.normalizeUrl("a-b_c.d~e"));
        // Gen-delims: / ? # [ ] @
        assertEquals("/path?query#frag[@]", UrlUtils.normalizeUrl("/path?query#frag[@]"));
        // Sub-delims: ! $ & ' ( ) * + , ; =
        assertEquals("!$&'()*+,;=", UrlUtils.normalizeUrl("!$&'()*+,;="));
        // Colon
        assertEquals("http://host:8080", UrlUtils.normalizeUrl("http://host:8080"));
        // Letters and digits
        assertEquals("abcXYZ019", UrlUtils.normalizeUrl("abcXYZ019"));
    }

    @Test
    void testNormalizeUrlWithHexDigitsAllRanges() {
        // Lowercase hex a-f
        assertEquals("path%2a%2f", UrlUtils.normalizeUrl("path%2a%2f"));
        // Uppercase hex A-F
        assertEquals("path%2A%2F", UrlUtils.normalizeUrl("path%2A%2F"));
        // Digits 0-9 in hex
        assertEquals("path%20%99", UrlUtils.normalizeUrl("path%20%99"));
    }
}
