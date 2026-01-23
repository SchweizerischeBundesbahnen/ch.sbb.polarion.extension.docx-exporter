package ch.sbb.polarion.extension.docx_exporter.converter;

import ch.sbb.polarion.extension.docx_exporter.pandoc.service.PandocServiceConnector;
import ch.sbb.polarion.extension.docx_exporter.pandoc.service.model.PandocParams;
import ch.sbb.polarion.extension.generic.regex.RegexMatcher;
import ch.sbb.polarion.extension.docx_exporter.properties.DocxExporterExtensionConfiguration;
import ch.sbb.polarion.extension.docx_exporter.settings.LocalizationSettings;
import ch.sbb.polarion.extension.docx_exporter.util.HtmlLogger;
import ch.sbb.polarion.extension.docx_exporter.util.HtmlProcessor;
import ch.sbb.polarion.extension.docx_exporter.util.DocxExporterFileResourceProvider;
import ch.sbb.polarion.extension.docx_exporter.util.html.HtmlLinksHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.List;

public class HtmlToDocxConverter {
    private final HtmlProcessor htmlProcessor;
    private final PandocServiceConnector pandocServiceConnector;

    public HtmlToDocxConverter() {
        DocxExporterFileResourceProvider fileResourceProvider = new DocxExporterFileResourceProvider();
        this.htmlProcessor = new HtmlProcessor(fileResourceProvider, new LocalizationSettings(), new HtmlLinksHelper(fileResourceProvider));
        this.pandocServiceConnector = new PandocServiceConnector();
    }

    @VisibleForTesting
    public HtmlToDocxConverter(HtmlProcessor htmlProcessor, PandocServiceConnector pandocServiceConnector) {
        this.htmlProcessor = htmlProcessor;
        this.pandocServiceConnector = pandocServiceConnector;
    }

    public byte[] convert(String origHtml, byte[] template, @NotNull PandocParams params) {
        validateHtml(origHtml);
        String html = preprocessHtml(origHtml);
        if (DocxExporterExtensionConfiguration.getInstance().isDebug()) {
            new HtmlLogger().log(origHtml, html, "");
        }
        return pandocServiceConnector.convertToDocx(origHtml, template, params);
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

        htmlProcessor.replaceLinks(document);

        String processedHtml = htmlProcessor.internalizeLinks(document.html());
        processedHtml = htmlProcessor.replaceResourcesAsBase64Encoded(processedHtml);

        return processedHtml;
    }

}
