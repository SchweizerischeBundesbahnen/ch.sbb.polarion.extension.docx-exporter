package ch.sbb.polarion.extension.docx_exporter.util;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.Nullable;

@UtilityClass
public class UrlUtils {

    /**
     * Encodes characters that are invalid in URIs (like spaces) while preserving already encoded sequences
     * and valid URI structure characters (like /, ?, #, =, &amp;).
     *
     * @param url the URL string to encode
     * @return the URL with invalid characters encoded, or null/empty if input was null/empty
     */
    public static @Nullable String encodeInvalidUriCharacters(@Nullable String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c == ' ') {
                result.append("%20");
            } else if (c == '%' && i + 2 < url.length()
                    && isHexDigit(url.charAt(i + 1))
                    && isHexDigit(url.charAt(i + 2))) {
                // Already encoded sequence, keep as is
                result.append(c);
            } else if (isValidUriCharacter(c)) {
                result.append(c);
            } else {
                // Encode other invalid characters
                result.append(String.format("%%%02X", (int) c));
            }
        }
        return result.toString();
    }

    private static boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static boolean isValidUriCharacter(char c) {
        // RFC 3986 unreserved characters + reserved characters used in URL structure
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                || c == '-' || c == '_' || c == '.' || c == '~'  // unreserved
                || c == '/' || c == '?' || c == '#' || c == '[' || c == ']' || c == '@'  // gen-delims
                || c == '!' || c == '$' || c == '&' || c == '\'' || c == '(' || c == ')'  // sub-delims
                || c == '*' || c == '+' || c == ',' || c == ';' || c == '=' || c == ':'
                || c == '%';  // percent (for already encoded sequences)
    }
}
