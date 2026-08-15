package ch.sbb.polarion.extension.docx_exporter.util.html;

import ch.sbb.polarion.extension.docx_exporter.util.FileResourceProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExternalCssInternalizerTest {
    @Mock
    private FileResourceProvider fileResourceProvider;

    @InjectMocks
    private ExternalCssInternalizer cssLinkInliner;


    @Test
    void shouldReturnEmptyResultForUnknownTags() {
        Optional<String> result = cssLinkInliner.inline(Map.of("rel", "unknown"));

        assertThat(result).isEmpty();
    }

    @Test
    void shouldConvertStylesheetLink() {
        when(fileResourceProvider.getResourceAsBytes("my-href-location")).thenReturn("test-stylesheet".getBytes());
        Optional<String> result = cssLinkInliner.inline(Map.of("rel", "stylesheet", "href", "my-href-location"));

        assertThat(result).contains("<style>test-stylesheet</style>");
    }

    @Test
    void shouldConvertStylesheetLinkAndTransferDataPrecedence() {
        when(fileResourceProvider.getResourceAsBytes("my-href-location")).thenReturn("test-stylesheet".getBytes());
        Optional<String> result = cssLinkInliner.inline(Map.of(
                "rel", "stylesheet",
                "href", "my-href-location",
                "data-precedence", "test-data-precedence"));

        assertThat(result).contains("""
                <style data-precedence="test-data-precedence">test-stylesheet</style>""");
    }

    @Test
    void shouldConvertStylesheetLinkAndProcessRelativeLinks() {
        when(fileResourceProvider.getResourceAsBase64String("/some/location/../fonts/some-font.woff")).thenReturn("data:font/woff;base64,Zm9udDE=");
        when(fileResourceProvider.getResourceAsBase64String("/some/location/relative/quotes/some-font2.woff")).thenReturn("data:font/woff;base64,Zm9udDI=");
        when(fileResourceProvider.getResourceAsBase64String("/some/location/relative/double/quotes/some-font3.woff")).thenReturn("data:font/woff;base64,Zm9udDM=");
        when(fileResourceProvider.getResourceAsBase64String("/some/location/relative/no/quotes/some-font4.woff")).thenReturn("data:font/woff;base64,Zm9udDQ=");
        when(fileResourceProvider.getResourceAsBase64String("/non-relative/fonts/some-font3.woff")).thenReturn("data:font/woff;base64,Zm9udDU=");
        when(fileResourceProvider.getResourceAsBytes("/some/location/file.css")).thenReturn("""
                @font-face {
                  src: url('../fonts/some-font.woff');
                }
                @font-face {
                  src: url('relative/quotes/some-font2.woff');
                }
                @font-face {
                  src: url("relative/double/quotes/some-font3.woff");
                }
                @font-face {
                  src: url(relative/no/quotes/some-font4.woff);
                }
                @font-face {
                  src: url('/non-relative/fonts/some-font3.woff');
                }
                """.getBytes());
        Optional<String> result = cssLinkInliner.inline(Map.of(
                "rel", "stylesheet",
                "href", "/some/location/file.css",
                "data-precedence", "test-data-precedence"));

        assertThat(result).isNotEmpty();
        // each font is asked for under the location of the stylesheet, and each is inlined from there
        assertThat(result.get()).contains(
                "src: url(data:font/woff;base64,Zm9udDE=)",
                "src: url(data:font/woff;base64,Zm9udDI=)",
                "src: url(data:font/woff;base64,Zm9udDM=)",
                "src: url(data:font/woff;base64,Zm9udDQ=)",
                "src: url(data:font/woff;base64,Zm9udDU=)"
        );
    }

    @Test
    void shouldReplaceAFontOfAStylesheetWhichCannotBeLoaded() {
        // pandoc-service reads a root path out of its own container, so a path which resolves to
        // nothing here must not stay in the document it converts
        when(fileResourceProvider.getResourceAsBytes("/some/location/file.css")).thenReturn("""
                @font-face {
                  src: url('/etc/hostname');
                }
                """.getBytes());

        Optional<String> result = cssLinkInliner.inline(Map.of("rel", "stylesheet", "href", "/some/location/file.css"));

        assertThat(result).isNotEmpty();
        assertThat(result.get()).doesNotContain("/etc/hostname");
    }

    @ParameterizedTest
    @ValueSource(strings = {"stylesheet", "Stylesheet", "STYLESHEET", "stylesheet ", " stylesheet", "alternate stylesheet"})
    void internalizesEverySpellingOfTheRelAStylesheetHas(String rel) {
        // a renderer reads rel as a list of tokens, each case insensitive, and loads all of these
        when(fileResourceProvider.getResourceAsBytes("my-href-location")).thenReturn("test-stylesheet".getBytes());

        Optional<String> result = cssLinkInliner.inline(Map.of("rel", rel, "href", "my-href-location"));

        assertThat(result).contains("<style>test-stylesheet</style>");
    }

    @ParameterizedTest
    @ValueSource(strings = {"icon", "preload", "stylesheets", "nostylesheet"})
    void keepsALinkWhichNamesNoStylesheet(String rel) {
        Optional<String> result = cssLinkInliner.inline(Map.of("rel", rel, "href", "my-href-location"));

        assertThat(result).isEmpty();
    }
}
