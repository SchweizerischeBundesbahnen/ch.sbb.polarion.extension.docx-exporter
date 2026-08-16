package ch.sbb.polarion.extension.docx_exporter.util.html;

import ch.sbb.polarion.extension.docx_exporter.util.FileResourceProvider;
import ch.sbb.polarion.extension.docx_exporter.util.MediaUtils;
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
    @ValueSource(strings = {"stylesheet", "Stylesheet", "STYLESHEET", "stylesheet ", " stylesheet"})
    void internalizesEverySpellingOfTheRelAStylesheetHas(String rel) {
        // a renderer reads rel as a list of tokens, each case insensitive, and loads all of these
        when(fileResourceProvider.getResourceAsBytes("my-href-location")).thenReturn("test-stylesheet".getBytes());

        Optional<String> result = cssLinkInliner.inline(Map.of("rel", rel, "href", "my-href-location"));

        assertThat(result).contains("<style>test-stylesheet</style>");
    }

    @ParameterizedTest
    @ValueSource(strings = {"icon", "preload", "stylesheets", "nostylesheet",
            // an alternative style sheet is skipped by a renderer until someone selects it, and nothing
            // selects one in an export: measured, weasyprint-service does not ask for such a link
            "alternate stylesheet", "stylesheet alternate", "Alternate Stylesheet"})
    void keepsALinkWhichNamesNoStylesheet(String rel) {
        Optional<String> result = cssLinkInliner.inline(Map.of("rel", rel, "href", "my-href-location"));

        assertThat(result).isEmpty();
    }

    @Test
    void shouldKeepAValueOfTheDocumentInsideItsAttribute() {
        // the value is written back as markup, so a quote in it may not end the attribute it sits in
        when(fileResourceProvider.getResourceAsBytes("my-href-location")).thenReturn("body{color:red}".getBytes());

        Optional<String> result = cssLinkInliner.inline(Map.of(
                "rel", "stylesheet",
                "href", "my-href-location",
                "data-precedence", "x\"><img src=http://169.254.169.254/x>"));

        assertThat(result).isPresent();
        assertThat(result.get())
                .doesNotContain("<img src=http://169.254.169.254/x>")
                .contains("&quot;&gt;&lt;img");
    }

    @Test
    void shouldKeepAStylesheetInsideItsStyleElement() {
        // a style element ends at the first closing tag written in it, and a stylesheet may carry one
        when(fileResourceProvider.getResourceAsBytes("my-href-location"))
                .thenReturn("a::after{content:\"</style><img src=http://169.254.169.254/x>\"}".getBytes());

        Optional<String> result = cssLinkInliner.inline(Map.of("rel", "stylesheet", "href", "my-href-location"));

        assertThat(result).isPresent();
        assertThat(result.get())
                .doesNotContain("</style><img")
                .endsWith("</style>");
    }

    @Test
    void shouldLeaveAUrlWrittenInsideAStringAlone() {
        // the location of the stylesheet is applied by the parser, which reads a string as a string:
        // a pattern over the text used to prefix this one as if it were a url of its own
        when(fileResourceProvider.getResourceAsBytes("/some/location/file.css")).thenReturn("""
                a::after { content: "url(not-a-resource.png)"; }
                b { background: url(picture.png); }
                """.getBytes());
        when(fileResourceProvider.getResourceAsBase64String("/some/location/picture.png"))
                .thenReturn("data:image/png;base64,AAAA");

        Optional<String> result = cssLinkInliner.inline(Map.of("rel", "stylesheet", "href", "/some/location/file.css"));

        assertThat(result).isPresent();
        assertThat(result.get())
                .contains("content: \"url(not-a-resource.png)\"")
                .contains("url(data:image/png;base64,AAAA)");
    }

    @Test
    void shouldReplaceAUrlOfTheStylesheetWhichCouldNotBeLoaded() {
        // pandoc-service reads a path from the root out of its own container, so a url which resolves
        // to nothing here becomes the placeholder rather than staying in the document
        when(fileResourceProvider.getResourceAsBytes("/some/location/file.css"))
                .thenReturn("a { background: url(picture.png); }".getBytes());

        Optional<String> result = cssLinkInliner.inline(Map.of("rel", "stylesheet", "href", "/some/location/file.css"));

        assertThat(result).isPresent();
        assertThat(result.get())
                .doesNotContain("picture.png")
                .contains(MediaUtils.BLOCKED_RESOURCE_PLACEHOLDER);
    }

    @Test
    void shouldKeepAResolvedUrlInsideItsTerm() {
        // the address was read inside quotes, so it may carry a bracket: written back without quotes it
        // would end the term and what follows it would be read as css of its own
        when(fileResourceProvider.getResourceAsBytes("/some/location/file.css")).thenReturn(
                "a { background: url(\"x.png) } b { background: url(http://169.254.169.254/x.png) } c { color: red\"); }"
                        .getBytes());

        Optional<String> result = cssLinkInliner.inline(Map.of("rel", "stylesheet", "href", "/some/location/file.css"));

        assertThat(result).isPresent();
        assertThat(result.get()).doesNotContain("url(http://169.254.169.254/x.png)");
    }

    @Test
    void shouldEscapeAResolvedUrlWhichStaysInTheStylesheet() {
        // the stylesheet was fetched from a relative location, so what it names stays relative and stays
        // in the text: the address is written back escaped, and a bracket in it cannot end the term
        when(fileResourceProvider.getResourceAsBytes("styles/file.css")).thenReturn(
                "a { background: url(\"x.png) } b { background: url(http://169.254.169.254/x.png) } c { color: red\"); }"
                        .getBytes());

        Optional<String> result = cssLinkInliner.inline(Map.of("rel", "stylesheet", "href", "styles/file.css"));

        assertThat(result).isPresent();
        assertThat(result.get())
                .doesNotContain("url(http://169.254.169.254/x.png)")
                .contains("styles/x.png");
    }

    @Test
    void shouldRemoveAnImportOfAFetchedStylesheet() {
        // its target is relative to the stylesheet, and a renderer would read it relative to the
        // document: what it would fetch is not what the stylesheet names, and nothing vetted it
        when(fileResourceProvider.getResourceAsBytes("/some/location/file.css"))
                .thenReturn("@import \"theme.css\"; a { color: red }".getBytes());

        Optional<String> result = cssLinkInliner.inline(Map.of("rel", "stylesheet", "href", "/some/location/file.css"));

        assertThat(result).isPresent();
        assertThat(result.get()).doesNotContain("theme.css").contains("color: red");
    }
}
