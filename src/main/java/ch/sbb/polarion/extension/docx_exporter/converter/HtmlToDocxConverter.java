package ch.sbb.polarion.extension.docx_exporter.converter;

import ch.sbb.polarion.extension.docx_exporter.constants.HtmlTag;
import ch.sbb.polarion.extension.docx_exporter.pandoc.service.PandocServiceConnector;
import ch.sbb.polarion.extension.docx_exporter.pandoc.service.model.PandocParams;
import ch.sbb.polarion.extension.generic.regex.RegexMatcher;
import ch.sbb.polarion.extension.docx_exporter.properties.DocxExporterExtensionConfiguration;
import ch.sbb.polarion.extension.docx_exporter.settings.LocalizationSettings;
import ch.sbb.polarion.extension.docx_exporter.util.HtmlLogger;
import ch.sbb.polarion.extension.docx_exporter.util.HtmlProcessor;
import ch.sbb.polarion.extension.docx_exporter.util.DocxExporterFileResourceProvider;
import ch.sbb.polarion.extension.docx_exporter.util.DocxTemplateProcessor;
import ch.sbb.polarion.extension.docx_exporter.util.html.HtmlLinksHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.List;

public class HtmlToDocxConverter {
    private final DocxTemplateProcessor docxTemplateProcessor;
    private final HtmlProcessor htmlProcessor;
    private final PandocServiceConnector pandocServiceConnector;

    public HtmlToDocxConverter() {
        this.docxTemplateProcessor = new DocxTemplateProcessor();
        DocxExporterFileResourceProvider fileResourceProvider = new DocxExporterFileResourceProvider();
        this.htmlProcessor = new HtmlProcessor(fileResourceProvider, new LocalizationSettings(), new HtmlLinksHelper(fileResourceProvider));
        this.pandocServiceConnector = new PandocServiceConnector();
    }

    @VisibleForTesting
    public HtmlToDocxConverter(DocxTemplateProcessor docxTemplateProcessor, HtmlProcessor htmlProcessor, PandocServiceConnector pandocServiceConnector) {
        this.docxTemplateProcessor = docxTemplateProcessor;
        this.htmlProcessor = htmlProcessor;
        this.pandocServiceConnector = pandocServiceConnector;
    }

    public byte[] convert(String origHtml, byte[] template, @Nullable List<String> options, @NotNull PandocParams params) {
        validateHtml(origHtml);
        String html = preprocessHtml(origHtml);
        if (DocxExporterExtensionConfiguration.getInstance().isDebug()) {
            new HtmlLogger().log(origHtml, html, "");
        }
        return pandocServiceConnector.convertToDocx(origHtml, template, options, params);
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
        Document document = Jsoup.parse(origHtml);
        Element head = document.head();
        @Nullable Element styleTag = head.selectFirst(HtmlTag.STYLE);

        String additionalCss = docxTemplateProcessor.buildSizeCss();
        if (additionalCss != null) {
            if (styleTag != null) {
                styleTag.appendText(additionalCss);
            } else {
                head.appendElement(HtmlTag.STYLE).text(additionalCss);
            }
        }

        htmlProcessor.replaceLinks(document);

        String processedHtml = htmlProcessor.internalizeLinks(document.html());
        processedHtml = htmlProcessor.replaceResourcesAsBase64Encoded(processedHtml);

        return processedHtml;
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
