package ch.sbb.polarion.extension.docx_exporter.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class LiveDocTOCGenerator {
    protected static final int START_DEFAULT_NODE_NESTING = 1;
    protected static final int MAX_DEFAULT_NODE_NESTING = 6;

    public void addTableOfContent(@NotNull Document document) {
        // find <pd4ml:toc> and replace
        Element tocPlaceholder = document.getElementsByTag("pd4ml:toc").first();
        if (tocPlaceholder != null) {
            int startLevel = tocPlaceholder.hasAttr("tocInit") ? Integer.parseInt(tocPlaceholder.attr("tocInit")) : START_DEFAULT_NODE_NESTING;
            int maxLevel = tocPlaceholder.hasAttr("tocMax") ? Integer.parseInt(tocPlaceholder.attr("tocMax")) : MAX_DEFAULT_NODE_NESTING;
            Element tocElement = generateTableOfContent(document, startLevel, maxLevel); // support h1-h6

            tocPlaceholder.before(tocElement);
            tocPlaceholder.remove();
        }
    }

    @NotNull
    private Element generateTableOfContent(@NotNull Document document, int startLevel, int maxLevel) {
        TocLeaf rootLeaf = new TocLeaf(null, 0, null, null, null);
        AtomicReference<TocLeaf> current = new AtomicReference<>(rootLeaf);

        // build selector for headings (h1-h6)
        String selector = getHeadingSelector(startLevel, maxLevel);
        Elements headings = document.select(selector);

        for (Element heading : headings) {
            int level = getLevel(heading);
            String id = getId(heading);
            String number = getNumber(heading);
            String text = getText(heading);

            TocLeaf parent;
            if (current.get().getLevel() < level) {
                parent = current.get();
            } else {
                parent = current.get().getParent();
                while (parent.getLevel() >= level) {
                    parent = parent.getParent();
                }
            }

            TocLeaf newLeaf = new TocLeaf(parent, level, id, number, text);
            parent.getChildren().add(newLeaf);
            current.set(newLeaf);
        }

        return rootLeaf.asTableOfContent(startLevel, maxLevel);
    }

    private int getLevel(@NotNull Element heading) {
        return Integer.parseInt(heading.tagName().substring(1)); // extract level from tag name (e.g., h1 -> 1)
    }

    private @Nullable String getId(@NotNull Element heading) {
        List<Node> childNodes = heading.childNodes();
        for (Node childNode : childNodes) {
            if (childNode instanceof Element childElement) {
                String childId = childElement.id();
                if (!childId.isEmpty()) {
                    return childId;
                }
            }
        }
        return null;
    }

    private @NotNull String getNumber(@NotNull Element heading) {
        String text = heading.text();
        int firstSpace = text.indexOf(' ');
        if (firstSpace != -1) {
            return text.substring(0, firstSpace);
        } else {
            return "";
        }
    }

    private @NotNull String getText(@NotNull Element heading) {
        String text = heading.text();
        int firstSpace = text.indexOf(' ');
        if (firstSpace != -1) {
            return text.substring(firstSpace + 1);
        } else {
            return "";
        }
    }

    private String getHeadingSelector(int startLevel, int maxLevel) {
        StringBuilder selector = new StringBuilder();
        for (int i = startLevel; i <= maxLevel; i++) {
            if (!selector.isEmpty()) {
                selector.append(", ");
            }
            selector.append("h").append(i); // add h1, h2, ... to selector
        }
        return selector.toString();
    }

}
