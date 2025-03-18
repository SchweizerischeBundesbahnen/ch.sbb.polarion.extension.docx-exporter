package ch.sbb.polarion.extension.docx_exporter.converter;

import ch.sbb.polarion.extension.generic.regex.RegexMatcher;
import ch.sbb.polarion.extension.docx_exporter.properties.DocxExporterExtensionConfiguration;
import ch.sbb.polarion.extension.docx_exporter.settings.LocalizationSettings;
import ch.sbb.polarion.extension.docx_exporter.util.HtmlLogger;
import ch.sbb.polarion.extension.docx_exporter.util.HtmlProcessor;
import ch.sbb.polarion.extension.docx_exporter.util.DocxExporterFileResourceProvider;
import ch.sbb.polarion.extension.docx_exporter.util.DocxTemplateProcessor;
import ch.sbb.polarion.extension.docx_exporter.util.html.HtmlLinksHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

public class HtmlToDocxConverter {
    private final DocxTemplateProcessor docxTemplateProcessor;
    private final HtmlProcessor htmlProcessor;

    public HtmlToDocxConverter() {
        this.docxTemplateProcessor = new DocxTemplateProcessor();
        DocxExporterFileResourceProvider fileResourceProvider = new DocxExporterFileResourceProvider();
        this.htmlProcessor = new HtmlProcessor(fileResourceProvider, new LocalizationSettings(), new HtmlLinksHelper(fileResourceProvider), null);
    }

    @VisibleForTesting
    public HtmlToDocxConverter(DocxTemplateProcessor docxTemplateProcessor, HtmlProcessor htmlProcessor) {
        this.docxTemplateProcessor = docxTemplateProcessor;
        this.htmlProcessor = htmlProcessor;
    }

    public byte[] convert(String origHtml) {
        validateHtml(origHtml);
        String html = preprocessHtml(origHtml);
        if (DocxExporterExtensionConfiguration.getInstance().isDebug()) {
            new HtmlLogger().log(origHtml, html, "");
        }
        return null;
    }

    private void validateHtml(String origHtml) {
        if (!checkTagExists(origHtml, "html") || !checkTagExists(origHtml, "body")) {
            throw new IllegalArgumentException("Input html is malformed, expected html and body tags");
        }
    }

    public boolean checkTagExists(String html, String tagName) {
        return RegexMatcher.get("<" + tagName + "(\\s[^>]*)?\\s*/?>").anyMatch(html);
    }

    @NotNull
    @VisibleForTesting
    String preprocessHtml(String origHtml) {
        String origHead = extractTagContent(origHtml, "head");
        String origCss = extractTagContent(origHead, "style");

        String head = origHead + docxTemplateProcessor.buildBaseUrlHeader();
        String css = origCss + docxTemplateProcessor.buildSizeCss();
        if (origCss.isBlank()) {
            head = head + String.format("<style>%s</style>", css);
        } else {
            head = replaceTagContent(head, "style", css);
        }
        String html;
        if (origHead.isBlank()) {
            html = addHeadTag(origHtml, head);
        } else {
            html = replaceTagContent(origHtml, "head", head);
        }
        html = htmlProcessor.replaceResourcesAsBase64Encoded(html);
        html = htmlProcessor.internalizeLinks(html);

        return html;
    }

    private String extractTagContent(String html, String tag) {
        return RegexMatcher.get("<" + tag + ">(.*?)</" + tag + ">", RegexMatcher.DOTALL)
                .findFirst(html, regexEngine -> regexEngine.group(1)).orElse("");
    }

    private String replaceTagContent(String container, String tag, String newContent) {
        return RegexMatcher.get("<" + tag + ">(.*?)</" + tag + ">", RegexMatcher.DOTALL)
                .replaceAll(container, "<" + tag + ">" + newContent + "</" + tag + ">");
    }

    private String addHeadTag(String html, String headContent) {
        String pattern = "(<html[^<>]*>)";
        return html.replaceAll(pattern, "$1<head>" + headContent + "</head>");
    }
}
