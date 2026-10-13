package ch.sbb.polarion.extension.docx_exporter.util;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Renders the rich text of a comment as escaped text lines joined with {@code <br/>}.
 * The comment is placed inside a paragraph of the document, where a block element such as
 * {@code <p>} or {@code <ol>} would close that paragraph and leave the comment body in the document.
 */
@UtilityClass
public class CommentTextRenderer {

    private static final String LINE_BREAK = "<br/>";
    private static final String BULLET = "\u2022 ";
    private static final Set<String> BLOCK_TAGS = Set.of(
            "p", "div", "li", "ul", "ol", "h1", "h2", "h3", "h4", "h5", "h6",
            "blockquote", "pre", "table", "tr", "dl", "dt", "dd");

    @NotNull
    public static String toInlineHtml(@Nullable String richText) {
        if (richText == null || richText.isBlank()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        collectLines(Jsoup.parseBodyFragment(richText).body(), lines, line);
        flushLine(lines, line);
        return lines.stream().map(Entities::escape).collect(Collectors.joining(LINE_BREAK));
    }

    private static void collectLines(@NotNull Element parent, @NotNull List<String> lines, @NotNull StringBuilder line) {
        for (Node node : parent.childNodes()) {
            if (node instanceof TextNode textNode) {
                line.append(textNode.getWholeText());
            } else if (node instanceof Element element) {
                String tagName = element.normalName();
                if ("br".equals(tagName)) {
                    flushLine(lines, line);
                } else if (BLOCK_TAGS.contains(tagName)) {
                    flushLine(lines, line);
                    if ("li".equals(tagName)) {
                        line.append(listItemPrefix(element));
                    }
                    collectLines(element, lines, line);
                    flushLine(lines, line);
                } else {
                    // Table cells stay on one line, separated by a space.
                    if ("td".equals(tagName) || "th".equals(tagName)) {
                        line.append(' ');
                    }
                    collectLines(element, lines, line);
                }
            }
        }
    }

    @NotNull
    private static String listItemPrefix(@NotNull Element listItem) {
        Element list = listItem.parent();
        if (list != null && "ol".equals(list.normalName())) {
            int position = 1;
            for (Element sibling : list.children()) {
                if (sibling == listItem) {
                    break;
                }
                if ("li".equals(sibling.normalName())) {
                    position++;
                }
            }
            return position + ". ";
        }
        return BULLET;
    }

    private static void flushLine(@NotNull List<String> lines, @NotNull StringBuilder line) {
        // Polarion's editor leaves zero-width spaces and non-breaking spaces in otherwise empty lines.
        String text = line.toString().replace("\u200B", "").replaceAll("[\\s\\u00A0]+", " ").trim();
        if (!text.isEmpty()) {
            lines.add(text);
        }
        line.setLength(0);
    }
}
