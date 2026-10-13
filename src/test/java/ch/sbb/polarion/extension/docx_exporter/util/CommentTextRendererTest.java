package ch.sbb.polarion.extension.docx_exporter.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommentTextRendererTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "<p><br/></p>", "<p>​​</p>"})
    void emptyCommentTest(String richText) {
        assertEquals("", CommentTextRenderer.toInlineHtml(richText));
    }

    @Test
    void plainTextTest() {
        assertEquals("A comment", CommentTextRenderer.toInlineHtml("A comment"));
    }

    @Test
    void escapesTextTest() {
        assertEquals("a &lt; b &amp; c", CommentTextRenderer.toInlineHtml("a &lt; b &amp; c"));
    }

    @Test
    void richTextTest() {
        String richText = """
                <p>QWQEQEQ 11111</p>
                <ol>
                    <li><span style="font-weight: bold;">111</span></li>
                    <li><span style="font-weight: bold;">222</span></li>
                    <li><span style="font-weight: bold;">333</span></li>
                </ol>
                <p><br/>r435435435<br/><br/>​​</p>
                <ul>
                    <li>aaaa</li>
                    <li>bbbb</li>
                    <li>cccc</li>
                </ul>
                """;
        assertEquals("QWQEQEQ 11111<br/>1. 111<br/>2. 222<br/>3. 333<br/>r435435435<br/>• aaaa<br/>• bbbb<br/>• cccc",
                CommentTextRenderer.toInlineHtml(richText));
    }

    @Test
    void nestedListTest() {
        String richText = "<ol><li>one<ul><li>inner</li></ul></li><li>two</li></ol>";
        assertEquals("1. one<br/>• inner<br/>2. two", CommentTextRenderer.toInlineHtml(richText));
    }

    @Test
    void inlineFormattingTest() {
        assertEquals("bold and italic text", CommentTextRenderer.toInlineHtml("<b>bold</b> and <i>italic</i> <a href=\"#\">text</a>"));
    }

    @Test
    void tableTest() {
        String richText = "<table><tr><td>a</td><td>b</td></tr><tr><td>c</td><td>d</td></tr></table>";
        assertEquals("a b<br/>c d", CommentTextRenderer.toInlineHtml(richText));
    }
}
