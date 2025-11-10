package ch.sbb.polarion.extension.docx_exporter.util;

import ch.sbb.polarion.extension.docx_exporter.constants.HtmlTag;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.jsoup.nodes.Node;

@UtilityClass
public class JSoupUtils {
    @NotNull
    public Document parseHtml(@NotNull String html) {
        Document document = Jsoup.parse(html);
        document.outputSettings()
                .syntax(Document.OutputSettings.Syntax.xml)
                .escapeMode(Entities.EscapeMode.base)
                .prettyPrint(false);
        return document;
    }

    public boolean isH1(@NotNull Node node) {
        return node instanceof Element element && element.tagName().equals(HtmlTag.H1);
    }

}
