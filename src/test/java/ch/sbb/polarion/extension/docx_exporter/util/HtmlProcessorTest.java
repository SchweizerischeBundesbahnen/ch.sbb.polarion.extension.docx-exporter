package ch.sbb.polarion.extension.docx_exporter.util;

import ch.sbb.polarion.extension.docx_exporter.TestStringUtils;
import ch.sbb.polarion.extension.docx_exporter.rest.model.conversion.ExportParams;
import ch.sbb.polarion.extension.docx_exporter.rest.model.settings.localization.Language;
import ch.sbb.polarion.extension.docx_exporter.rest.model.settings.localization.LocalizationModel;
import ch.sbb.polarion.extension.docx_exporter.service.DocxExporterPolarionService;
import ch.sbb.polarion.extension.docx_exporter.settings.LocalizationSettings;
import ch.sbb.polarion.extension.docx_exporter.util.html.HtmlLinksHelper;
import ch.sbb.polarion.extension.generic.settings.SettingId;
import com.polarion.core.boot.PolarionProperties;
import lombok.SneakyThrows;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("ConstantConditions")
class HtmlProcessorTest {

    @Mock
    private DocxExporterFileResourceProvider fileResourceProvider;
    @Mock
    private LocalizationSettings localizationSettings;
    @Mock
    private HtmlLinksHelper htmlLinksHelper;
    @Mock
    private DocxExporterPolarionService docxExporterPolarionService;

    private HtmlProcessor processor;

    @BeforeEach
    void init() {
        processor = new HtmlProcessor(fileResourceProvider, localizationSettings, htmlLinksHelper, docxExporterPolarionService);
        Map<String, String> deTranslations = Map.of(
                "draft", "Entwurf",
                "not reviewed", "Nicht überprüft"
        );
        Map<String, String> frTranslations = Map.of(
                "draft", "Projet",
                "not reviewed", "Non revu"
        );
        Map<String, String> itTranslations = Map.of(
                "draft", "Bozza",
                "not reviewed", "Non rivisto"
        );
        LocalizationModel localizationModel = new LocalizationModel(deTranslations, frTranslations, itTranslations);

        lenient().when(localizationSettings.load(anyString(), any(SettingId.class))).thenReturn(localizationModel);
    }

    @Test
    void cutLocalUrlsTest() {
        String img = "<img title=\"diagram_123.png\" src=\"data:image/png;BASE64Content\"/>";
        String imgInsideA = String.format("<a href=\"http://localhost/polarion/module-attachment/elibrary/some-path\">%s</a>", img);
        assertEquals(img, processor.cutLocalUrls(imgInsideA));

        String span = "<span id=\"PLANID_Version_1_0\" title=\"Version 1.0 (Version_1_0) (2017-03-31)\" class=\"polarion-Plan\">Version 1.0<span> (2017-03-31)</span></span>";
        String spanInsideA = String.format("<a href=\"http://localhost/polarion/#/project/elibrary/another-path\">%s</a>", span);
        assertEquals(span, processor.cutLocalUrls(spanInsideA));
    }

    @Test
    @SneakyThrows
    void cutLocalUrlsWithRolesFilteringTest() {
        when(localizationSettings.load(any(), any(SettingId.class))).thenReturn(new LocalizationModel(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap()));
        when(docxExporterPolarionService.getPolarionVersion()).thenReturn("2310");

        try (InputStream isInvalidHtml = this.getClass().getResourceAsStream("/cutLocalUrlsWithRolesFilteringBeforeProcessing.html");
             InputStream isValidHtml = this.getClass().getResourceAsStream("/cutLocalUrlsWithRolesFilteringAfterProcessing.html")) {

            ExportParams exportParams = getExportParams();
            exportParams.setCutLocalUrls(true);

            String invalidHtml = new String(isInvalidHtml.readAllBytes(), StandardCharsets.UTF_8);

            List<String> selectedRoles = Arrays.asList("has parent", "is parent of", "depends on", "blocks", "verifies", "is verified by");
            // Spaces and new lines are removed to exclude difference in space characters
            String fixedHtml = processor.processHtmlForPDF(invalidHtml, exportParams, selectedRoles);
            String validHtml = new String(isValidHtml.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(TestStringUtils.removeNonsensicalSymbols(validHtml), TestStringUtils.removeNonsensicalSymbols(fixedHtml));
        }
    }

    @Test
    @SneakyThrows
    void processPageBrakesTest() {
        try (InputStream isInvalidHtml = this.getClass().getResourceAsStream("/pageBreaksBeforeProcessing.html");
             InputStream isValidHtml = this.getClass().getResourceAsStream("/pageBreaksAfterProcessing.html")) {

            String invalidHtml = new String(isInvalidHtml.readAllBytes(), StandardCharsets.UTF_8);

            // Spaces and new lines are removed to exclude difference in space characters
            String fixedHtml = processor.processPageBrakes(invalidHtml);
            String validHtml = new String(isValidHtml.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(TestStringUtils.removeNonsensicalSymbols(validHtml), TestStringUtils.removeNonsensicalSymbols(fixedHtml));
        }
    }

    @Test
    @SneakyThrows
    void cutEmptyChaptersTest() {
        try (InputStream isInvalidHtml = this.getClass().getResourceAsStream("/emptyChaptersBeforeProcessing.html");
             InputStream isValidHtml = this.getClass().getResourceAsStream("/emptyChaptersAfterProcessing.html")) {

            Document invalidDocument = Jsoup.parse(new String(isInvalidHtml.readAllBytes(), StandardCharsets.UTF_8));
            invalidDocument.outputSettings()
                    .syntax(Document.OutputSettings.Syntax.xml)
                    .escapeMode(Entities.EscapeMode.base)
                    .prettyPrint(false);

            // Spaces and new lines are removed to exclude difference in space characters
            String fixedHtml = processor.cutEmptyChapters(invalidDocument).body().html();
            String validHtml = new String(isValidHtml.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(TestStringUtils.removeNonsensicalSymbols(validHtml), TestStringUtils.removeNonsensicalSymbols(fixedHtml));
        }
    }

    @Test
    @SneakyThrows
    void cutEmptyWIAttributesTest() {
        try (InputStream isInvalidHtml = this.getClass().getResourceAsStream("/emptyWIAttributesBeforeProcessing.html");
             InputStream isValidHtml = this.getClass().getResourceAsStream("/emptyWIAttributesAfterProcessing.html")) {

            Document document = JSoupUtils.parseHtml(new String(isInvalidHtml.readAllBytes(), StandardCharsets.UTF_8));

            processor.cutEmptyWIAttributes(document);
            String fixedHtml = document.body().html();
            String validHtml = new String(isValidHtml.readAllBytes(), StandardCharsets.UTF_8);

            // Spaces and new lines are removed to exclude difference in space characters
            assertEquals(TestStringUtils.removeNonsensicalSymbols(validHtml), TestStringUtils.removeNonsensicalSymbols(fixedHtml));
        }
    }

    @Test
    @SneakyThrows
    void properTableHeadsTest() {
        try (InputStream isInvalidHtml = this.getClass().getResourceAsStream("/invalidTableHeads.html");
             InputStream isValidHtml = this.getClass().getResourceAsStream("/validTableHeads.html")) {

            String invalidHtml = new String(isInvalidHtml.readAllBytes(), StandardCharsets.UTF_8);

            // Spaces and new lines are removed to exclude difference in space characters
            String fixedHtml = processor.properTableHeads(invalidHtml);
            String validHtml = new String(isValidHtml.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(TestStringUtils.removeNonsensicalSymbols(validHtml), TestStringUtils.removeNonsensicalSymbols(fixedHtml));
        }
    }

    @Test
    @SneakyThrows
    void adjustCellWidthTest() {
        try (InputStream isInvalidHtml = this.getClass().getResourceAsStream("/cellWidthBeforeProcessing.html");
             InputStream isValidHtml = this.getClass().getResourceAsStream("/cellWidthAfterProcessing.html")) {

            Document document = JSoupUtils.parseHtml(new String(isInvalidHtml.readAllBytes(), StandardCharsets.UTF_8));

            processor.adjustCellWidth(document);
            String fixedHtml = document.body().html();
            String validHtml = new String(isValidHtml.readAllBytes(), StandardCharsets.UTF_8);

            // Spaces and new lines are removed to exclude difference in space characters
            assertEquals(TestStringUtils.removeNonsensicalSymbols(validHtml), TestStringUtils.removeNonsensicalSymbols(fixedHtml));
        }
    }

    @Test
    @SneakyThrows
    void cutNotNeededChaptersTest() {
        try (InputStream isInvalidHtml = this.getClass().getResourceAsStream("/notNeededChaptersBeforeProcessing.html");
             InputStream isValidHtml = this.getClass().getResourceAsStream("/notNeededChaptersAfterProcessing.html")) {

            String invalidHtml = new String(isInvalidHtml.readAllBytes(), StandardCharsets.UTF_8);

            // Spaces and new lines are removed to exclude difference in space characters
            String fixedHtml = processor.cutNotNeededChapters(invalidHtml, Collections.singletonList("2"));
            String validHtml = new String(isValidHtml.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(TestStringUtils.removeNonsensicalSymbols(validHtml), TestStringUtils.removeNonsensicalSymbols(fixedHtml));
        }
    }

    @Test
    @SneakyThrows
    void localizeEnumsTest() {
        try (InputStream isInvalidHtml = this.getClass().getResourceAsStream("/localizeEnumsBeforeProcessing.html");
             InputStream isValidHtml = this.getClass().getResourceAsStream("/localizeEnumsAfterProcessing.html")) {

            String invalidHtml = new String(isInvalidHtml.readAllBytes(), StandardCharsets.UTF_8);

            // Spaces and new lines are removed to exclude difference in space characters
            String fixedHtml = processor.localizeEnums(invalidHtml, getExportParams());
            String validHtml = new String(isValidHtml.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(TestStringUtils.removeNonsensicalSymbols(validHtml), TestStringUtils.removeNonsensicalSymbols(fixedHtml));
        }
    }

    @Test
    @SneakyThrows
    void selectLinkedWorkItemTypesTableTest() {
        when(docxExporterPolarionService.getPolarionVersion()).thenReturn("2404");

        try (InputStream isInvalidHtml = this.getClass().getResourceAsStream("/linkedWorkItemsTableBeforeProcessing.html");
             InputStream isValidHtml = this.getClass().getResourceAsStream("/linkedWorkItemsTableAfterProcessing.html")) {

            String invalidHtml = new String(isInvalidHtml.readAllBytes(), StandardCharsets.UTF_8);

            ExportParams exportParams = getExportParams();
            exportParams.setLinkedWorkitemRoles(List.of("has parent"));

            List<String> selectedRoleEnumValues = Arrays.asList("has parent", "is parent of");

            // Spaces and new lines are removed to exclude difference in space characters
            String fixedHtml = processor.processHtmlForPDF(invalidHtml, exportParams, selectedRoleEnumValues);
            String validHtml = new String(isValidHtml.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(TestStringUtils.removeNonsensicalSymbols(validHtml), TestStringUtils.removeNonsensicalSymbols(fixedHtml));
        }
    }

    @Test
    @SneakyThrows
    void selectLinkedWorkItemTypesTest() {
        when(docxExporterPolarionService.getPolarionVersion()).thenReturn(null);

        try (InputStream isInvalidHtml = this.getClass().getResourceAsStream("/linkedWorkItemsBeforeProcessing.html");
             InputStream isValidHtml = this.getClass().getResourceAsStream("/linkedWorkItemsAfterProcessing.html")) {

            String invalidHtml = new String(isInvalidHtml.readAllBytes(), StandardCharsets.UTF_8);

            ExportParams exportParams = getExportParams();
            exportParams.setLinkedWorkitemRoles(List.of("has parent"));

            List<String> selectedRoleEnumValues = Arrays.asList("has parent", "is parent of");

            // Spaces and new lines are removed to exclude difference in space characters
            String fixedHtml = processor.processHtmlForPDF(invalidHtml, exportParams, selectedRoleEnumValues);
            String validHtml = new String(isValidHtml.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(TestStringUtils.removeNonsensicalSymbols(validHtml), TestStringUtils.removeNonsensicalSymbols(fixedHtml));
        }
    }

    @Test
    @SneakyThrows
    void nothingToReplaceTest() {
        String html = "<div></div>";
        String result = processor.replaceResourcesAsBase64Encoded(html);
        assertEquals("<div></div>", result);
    }

    @Test
    @SneakyThrows
    void replaceImagesAsBase64EncodedTest() {
        String html = "<div><img id=\"image\" src=\"http://localhost/some-path/img.png\"/></div>";
        when(fileResourceProvider.getResourceAsBase64String(any())).thenReturn("base64Data");
        String result = processor.replaceResourcesAsBase64Encoded(html);
        assertEquals("<div><img id=\"image\" src=\"base64Data\"/></div>", result);
    }

    @Test
    @SneakyThrows
    void replaceSvgImagesAsBase64EncodedTest() {
        String html = "<div><img id=\"image1\" src=\"http://localhost/some-path/img1.svg\"/> <img id='image2' src='http://localhost/some-path/img2.svg'/> <img id='image1' src='http://localhost/some-path/img1.svg'/></div>";
        byte[] imgBytes;
        try (InputStream is = new ByteArrayInputStream("<svg><switch><g requiredFeatures=\"http://www.w3.org/TR/SVG11/feature#Extensibility\"/></switch></svg>".getBytes(StandardCharsets.UTF_8))) {
            imgBytes = is.readAllBytes();
        }
        when(fileResourceProvider.getResourceAsBytes(any())).thenReturn(imgBytes);
        when(fileResourceProvider.getResourceAsBase64String(any())).thenCallRealMethod();
        when(fileResourceProvider.processPossibleSvgImage(any())).thenCallRealMethod();
        String result = processor.replaceResourcesAsBase64Encoded(html);
        String expected = "<div><img id=\"image1\" src=\"data:image/svg+xml;base64," + Base64.getEncoder().encodeToString("<svg></svg>".getBytes(StandardCharsets.UTF_8)) + "\"/> " +
                "<img id='image2' src='data:image/svg+xml;base64," + Base64.getEncoder().encodeToString("<svg></svg>".getBytes(StandardCharsets.UTF_8)) + "'/> " +
                "<img id='image1' src='data:image/svg+xml;base64," + Base64.getEncoder().encodeToString("<svg></svg>".getBytes(StandardCharsets.UTF_8)) + "'/></div>";
        assertEquals(expected, result);
    }

    @Test
    @SneakyThrows
    void processHtmlForPDFTestCutEmptyWorkItemAttributesDisabled() {
        try (InputStream isHtml = this.getClass().getResourceAsStream("/emptyWIAttributesBeforeProcessing.html")) {

            String html = new String(isHtml.readAllBytes(), StandardCharsets.UTF_8);

            HtmlProcessor spyHtmlProcessor = spy(processor);
            ExportParams exportParams = getExportParams();
            // to avoid changing input html and check with regular equals
            doNothing().when(spyHtmlProcessor).adjustCellWidth(any());
            exportParams.setCutEmptyChapters(false);

            // Spaces, new lines & nbsp symbols are removed to exclude difference in space characters
            String result = spyHtmlProcessor.processHtmlForPDF(html, exportParams, List.of());
            assertEquals(TestStringUtils.removeNonsensicalSymbols(html.replaceAll("&nbsp;|\u00A0", " ")), TestStringUtils.removeNonsensicalSymbols(result));
        }
    }

    @Test
    void adjustHeadingForPDFTestH1ReplacedWithDiv() {
        String html = "<h1>First level heading</h1>";
        String result = processor.processHtmlForPDF(html, getExportParams(), List.of());
        assertEquals("<div class=\"title\">First level heading</div>", result);
    }

    @Test
    void adjustHeadingForPDFTestLiftHeadingTag() {
        String html = "<h2>First level heading</h2>";
        String result = processor.processHtmlForPDF(html, getExportParams(), List.of());
        assertEquals("<h1>First level heading</h1>", result);
    }

    @Test
    void replaceDollars() {
        String html = "<div>100$</div>";
        String result = processor.processHtmlForPDF(html, getExportParams(), List.of());
        assertEquals("<div>100&dollar;</div>", result);
    }

    @Test
    @SneakyThrows
    void adjustContentToFitPageTest() {
        try (InputStream isInvalidHtml = this.getClass().getResourceAsStream("/fitToPageBeforeProcessing.html");
             InputStream isValidPortraitHtml = this.getClass().getResourceAsStream("/fitToPortraitPageAfterProcessing.html");
             InputStream isValidLandscapeHtml = this.getClass().getResourceAsStream("/fitToLandscapePageAfterProcessing.html")) {

            String invalidHtml = new String(isInvalidHtml.readAllBytes(), StandardCharsets.UTF_8);

            // Spaces and new lines are removed to exclude difference in space characters
            String fixedHtml = processor.adjustContentToFitPage(invalidHtml);
            String validHtml = new String(isValidPortraitHtml.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(TestStringUtils.removeNonsensicalSymbols(validHtml), TestStringUtils.removeNonsensicalSymbols(fixedHtml));

            fixedHtml = processor.adjustContentToFitPage(invalidHtml);
            validHtml = new String(isValidLandscapeHtml.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(TestStringUtils.removeNonsensicalSymbols(validHtml), TestStringUtils.removeNonsensicalSymbols(fixedHtml));
        }
    }

    @Test
    @SneakyThrows
    void adjustImagesInTablesTest() {
        try (InputStream isInvalidHtml = this.getClass().getResourceAsStream("/fitImagesWidthBeforeProcessing.html");
             InputStream isValidHtml = this.getClass().getResourceAsStream("/fitImagesWidthAfterProcessing.html")) {

            String invalidHtml = new String(isInvalidHtml.readAllBytes(), StandardCharsets.UTF_8);

            // Spaces and new lines are removed to exclude difference in space characters
            String fixedHtml = processor.adjustContentToFitPage(invalidHtml);
            String validHtml = new String(isValidHtml.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(TestStringUtils.removeNonsensicalSymbols(validHtml), TestStringUtils.removeNonsensicalSymbols(fixedHtml));
        }
    }

    @Test
    @SneakyThrows
    void cutExtraNbspTest() {
        try (InputStream initialHtml = this.getClass().getResourceAsStream("/extraNbspBeforeProcessing.html");
             InputStream resultHtml = this.getClass().getResourceAsStream("/extraNbspAfterProcessing.html")) {

            String initialHtmlString = new String(initialHtml.readAllBytes(), StandardCharsets.UTF_8);

            String fixedHtml = processor.cutExtraNbsp(initialHtmlString);
            String expectedHtml = new String(resultHtml.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(TestStringUtils.removeNonsensicalSymbols(expectedHtml), TestStringUtils.removeNonsensicalSymbols(fixedHtml));
        }
    }

    @Test
    @SneakyThrows
    void tableOfContentTest() {
        try (InputStream isInvalidHtml = this.getClass().getResourceAsStream("/tableOfContentBeforeProcessing.html");
             InputStream isInvalidFormattedHtml = this.getClass().getResourceAsStream("/tableOfContentBeforeProcessingFormatted.html");
             InputStream isValidHtml = this.getClass().getResourceAsStream("/tableOfContentAfterProcessing.html")) {

            String invalidHtml = new String(isInvalidHtml.readAllBytes(), StandardCharsets.UTF_8);
            String invalidFormattedHtml = new String(isInvalidFormattedHtml.readAllBytes(), StandardCharsets.UTF_8);

            // Spaces and new lines are removed to exclude difference in space characters
            String fixedHtml = processor.addTableOfContent(invalidHtml);
            String validHtml = new String(isValidHtml.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(TestStringUtils.removeNonsensicalSymbols(validHtml), TestStringUtils.removeNonsensicalSymbols(fixedHtml));

            String fixedFormattedHtml = processor.addTableOfContent(invalidFormattedHtml);
            assertEquals(TestStringUtils.removeNonsensicalSymbols(validHtml), TestStringUtils.removeNonsensicalSymbols(fixedFormattedHtml));
        }
    }

    @Test
    @SneakyThrows
    void tableOfTablesTest() {
        try (InputStream isInvalidHtml = this.getClass().getResourceAsStream("/tableOfTablesBeforeProcessing.html");
             InputStream isValidHtml = this.getClass().getResourceAsStream("/tableOfTablesAfterProcessing.html")) {

            String invalidHtml = new String(isInvalidHtml.readAllBytes(), StandardCharsets.UTF_8);

            // Spaces and new lines are removed to exclude difference in space characters
            String fixedHtml = processor.addTableOfFigures(invalidHtml);
            String validHtml = new String(isValidHtml.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(TestStringUtils.removeNonsensicalSymbols(validHtml), TestStringUtils.removeNonsensicalSymbols(fixedHtml));
        }
    }

    @Test
    @SneakyThrows
    void tableOfFiguresTest() {
        try (InputStream isInvalidHtml = this.getClass().getResourceAsStream("/tableOfFiguresBeforeProcessing.html");
             InputStream isValidHtml = this.getClass().getResourceAsStream("/tableOfFiguresAfterProcessing.html")) {

            String invalidHtml = new String(isInvalidHtml.readAllBytes(), StandardCharsets.UTF_8);

            // Spaces and new lines are removed to exclude difference in space characters
            String fixedHtml = processor.addTableOfFigures(invalidHtml);
            String validHtml = new String(isValidHtml.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(TestStringUtils.removeNonsensicalSymbols(validHtml), TestStringUtils.removeNonsensicalSymbols(fixedHtml));
        }
    }

    @Test
    @SneakyThrows
    void adjustReportedByTest() {
        String initialHtml = """
                    <p id="polarion_1">
                      <span class="polarion-rp-inline-widget" data-widget="com.polarion.scriptInline" id="polarion_1_iw_1">
                        <span id="polarion-rp-widget-content">
                          <div style="color: grey; text-align: right; position: absolute; top: 22px; right: 10px">Reported by
                            <span class="polarion-no-style-cleanup">System Administrator</span>
                            <br/>
                            January 12, 2024 at 4:19:27 PM UTC
                          </div>
                        </span>
                      </span>
                    </p>
                """;
        String processedHtml = processor.adjustReportedBy(initialHtml);

        String expectedHtml = """
                    <p id="polarion_1">
                      <span class="polarion-rp-inline-widget" data-widget="com.polarion.scriptInline" id="polarion_1_iw_1">
                        <span id="polarion-rp-widget-content">
                          <div style="color: grey; text-align: right; position: absolute; top: 22px; right: 10px; top: 0; font-size: 8px;">Reported by
                            <span class="polarion-no-style-cleanup">System Administrator</span>
                            <br/>
                            January 12, 2024 at 4:19:27 PM UTC
                          </div>
                        </span>
                      </span>
                    </p>
                """;
        assertEquals(TestStringUtils.removeNonsensicalSymbols(expectedHtml), TestStringUtils.removeNonsensicalSymbols(processedHtml.replaceAll(" ", "")));
    }

    @Test
    @SneakyThrows
    void cutExportToPdfButtonTest() {
        String initialHtml = """
                    <p id="polarion_client33">
                      <span class="polarion-rp-inline-widget" data-widget="ch.sbb.polarion.extension.pdf.exporter.widgets.exportToPdfButton" id="polarion_client33_iw_1">
                        <span id="polarion-rp-widget-content">
                          <span class="polarion-TestsExecutionButton-link">
                            <a onclick="PdfExporter.openPopup({context: &#39;report&#39;})">
                              <div style="color:#5E5E5E; text-shadow:#FCFCFC 1px 1px; border-color:#C2C2C2; background-color:#F0F0F0; " class="polarion-TestsExecutionButton-buttons-pdf">
                                <div style="height: 10px;"></div>
                                <table class="polarion-TestsExecutionButton-buttons-content">
                                  <tr>
                                    <td class="polarion-TestsExecutionButton-buttons-content-labelCell polarion-TestsExecutionButton-buttons-content-labelCell-noSumText">
                                      <div class="polarion-TestsExecutionButton-labelTextNew">Export to PDF</div>
                                    </td>
                                  </tr>
                                  <tr>
                                    <td style="color:#5E5E5E; text-shadow:#FCFCFC 1px 1px; " class="polarion-TestsExecutionButton-sumText"></td>
                                  </tr>
                                </table>
                              </div>
                            </a>
                          </span>
                        </span>
                      </span>
                    </p>
                """;
        String processedHtml = processor.cutExportToPdfButton(initialHtml);

        String expectedHtml = """
                    <p id="polarion_client33">
                      <span class="polarion-rp-inline-widget" data-widget="ch.sbb.polarion.extension.pdf.exporter.widgets.exportToPdfButton" id="polarion_client33_iw_1">
                        <span id="polarion-rp-widget-content">
                          <span class="polarion-TestsExecutionButton-link">
                            <a onclick="PdfExporter.openPopup({context: &#39;report&#39;})">
                            </a>
                          </span>
                        </span>
                      </span>
                    </p>
                """;
        assertEquals(TestStringUtils.removeNonsensicalSymbols(expectedHtml), TestStringUtils.removeNonsensicalSymbols(processedHtml.replaceAll(" ", "")));
    }

    @Test
    @SneakyThrows
    void adjustColumnWidthInReportsTest() {
        String initialHtml = """
                    <table class="polarion-rp-column-layout" style="width: 1000px;">
                      <tbody>
                        <tr>
                          <td class="polarion-rp-column-layout-cell" style="width: 100%;">
                            <h1 id="polarion_hardcoded_0">PDF Tests</h1>
                            <p id="polarion_hardcoded_1">This Page has no content yet</p>
                          </td>
                        </tr>
                      </tbody>
                    </table>
                """;
        String processedHtml = processor.adjustColumnWidthInReports(initialHtml);

        String expectedHtml = """
                    <table class="polarion-rp-column-layout" style="width: 100%;">
                      <tbody>
                        <tr>
                          <td class="polarion-rp-column-layout-cell" style="width: 100%;">
                            <h1 id="polarion_hardcoded_0">PDF Tests</h1>
                            <p id="polarion_hardcoded_1">This Page has no content yet</p>
                          </td>
                        </tr>
                      </tbody>
                    </table>
                """;
        assertEquals(TestStringUtils.removeNonsensicalSymbols(expectedHtml), TestStringUtils.removeNonsensicalSymbols(processedHtml.replaceAll(" ", "")));
    }

    @Test
    @SneakyThrows
    void removeFloatLeftFromReportsTest() {
        String initialHtml = """
                    <table id="polarion_client20" style="float: left;">
                      <tbody>
                        <tr>
                          <td>Status</td>
                          <td><span title="Accepted">Accepted</span></td>
                        </tr>
                        <tr>
                          <td>Severity</td>
                          <td><span title="Nice to Have">Nice to Have</span></td>
                        </tr>
                      </tbody>
                    </table>
                    <div style="clear: both;"></div>
                """;
        String processedHtml = processor.removeFloatLeftFromReports(initialHtml);

        String expectedHtml = """
                    <table id="polarion_client20" >
                      <tbody>
                        <tr>
                          <td>Status</td>
                          <td><span title="Accepted">Accepted</span></td>
                        </tr>
                        <tr>
                          <td>Severity</td>
                          <td><span title="Nice to Have">Nice to Have</span></td>
                        </tr>
                      </tbody>
                    </table>
                    <div style="clear: both;"></div>
                """;
        assertEquals(TestStringUtils.removeNonsensicalSymbols(expectedHtml), TestStringUtils.removeNonsensicalSymbols(processedHtml.replaceAll(" ", "")));
    }

    @Test
    void getImageWidthBasedOnColumnsCountTest() {
        assertEquals(-1, processor.getImageWidthBasedOnColumnsCount("<tr><img/></tr>", "<img/>"));
        assertEquals(-1, processor.getImageWidthBasedOnColumnsCount("<tr><td></td><td><img/></td><td></td>", "<img/>"));
        assertEquals(197, processor.getImageWidthBasedOnColumnsCount("<tr><td></td><td><img/></td><td></td></tr>", "<img/>"));
    }


    @Test
    void columnsCountTest() {
        assertEquals(0, processor.columnsCount(""));
        assertEquals(0, processor.columnsCount("<div></div>"));
        assertEquals(3, processor.columnsCount("<div><td></td><td><span/></td><td></div>"));
    }

    @Test
    void commentsTest() {
        String initialHtml = """
                    <html>
                        <head></head>
                        <body>
                            <span class='comment level-0'>
                                <span class='meta'>
                                    <span class='date'>2023-06-01 09:14</span>
                                    <span class='author'>System Administrator</span>
                                </span>
                                <span class='text'>A comment</span>
                            </span>
                            <span class='comment level-1'>
                                <span class='meta'>
                                    <span class='date'>2023-06-02 14:53</span>
                                    <span class='author'>System Administrator</span>
                                </span>
                                <span class='text'>Another comment</span>
                            </span>
                        </body>
                    </html>
                """;
        String processedHtml = processor.processComments(initialHtml);

        String expectedHtml = """
                    <html>
                        <head></head>
                        <body>
                            <span class="comment-start" id="0" author="System Administrator" date="2023-06-0109:14">A comment</span><span class="comment-end" id="0"></span>
                            <span class="comment-start" id="1" author="System Administrator" date="2023-06-0214:53">Another comment</span><span class="comment-end" id="1"></span>
                        </body>
                    </html>
                """;

        assertEquals(TestStringUtils.removeNonsensicalSymbols(expectedHtml), TestStringUtils.removeNonsensicalSymbols(processedHtml.replaceAll(" ", "")));
    }

    @Test
    void replaceRelativeLinkTest() {
        String baseUrl = "http://example.com/base/";
        System.setProperty(PolarionProperties.BASE_URL, baseUrl);
        try {
            String html = "<a href=\"page.html\">Page</a>";
            String result = processor.replaceLinks(html);
            assertTrue(result.contains("href=\"http://example.com/base/page.html\""));
        } finally {
            System.setProperty(PolarionProperties.BASE_URL, "");
        }
    }

    @Test
    void ignoreAbsoluteLinkTest() {
        String html = "<a href=\"https://external.com/page\">External</a>";
        String result = processor.replaceLinks(html);
        assertEquals(html, result);
    }

    @Test
    void ignoreMailtoLinkTest() {
        String html = "<a href=\"mailto:someone@example.com\">Email</a>";
        String result = processor.replaceLinks(html);
        assertEquals(html, result);
    }

    @Test
    void ignoreDleCaptionAnchorTest() {
        String html = "<a href=\"#dlecaption_123\">Caption</a>";
        String result = processor.replaceLinks(html);
        assertEquals(html, result);
    }

    @Test
    void malformedHrefIsSafeTest() {
        String baseUrl = "http://example.com/base/";
        System.setProperty(PolarionProperties.BASE_URL, baseUrl);
        try {
            String html = "<a href=\"..//page\">Broken path</a>";
            String result = processor.replaceLinks(html);
            assertTrue(result.contains("http://example.com/page"));
        } finally {
            System.setProperty(PolarionProperties.BASE_URL, "");
        }
    }

    @Test
    void testIsRelativeLink() {
        assertTrue(processor.isRelativeLink("about.html"));
        assertFalse(processor.isRelativeLink("http://foo.com"));
        assertFalse(processor.isRelativeLink("mailto:someone"));
        assertFalse(processor.isRelativeLink("#section1"));
    }

    @Test
    void testResolveUrl() {
        String baseUrl = "http://example.com/base/";
        String rel = "foo/bar.html";
        String result = processor.resolveUrl(baseUrl, rel);
        assertEquals("http://example.com/base/foo/bar.html", result);
    }

    @Test
    void processHtmlForPDFWithRemovalSelectorTest() {
        String html = "<html><body><div class='remove-me'>Should be removed</div><div class='keep-me'>Should be kept</div></body></html>";
        ExportParams exportParams = getExportParams();
        exportParams.setRemovalSelector(".remove-me");

        String result = processor.processHtmlForPDF(html, exportParams, List.of());

        assertFalse(result.contains("Should be removed"));
        assertTrue(result.contains("Should be kept"));
    }

    @Test
    void clearSelectorsRemovesMatchingElementsTest() {
        String html = "<html><body><div class='foo'>Content 1</div><div class='bar'>Content 2</div><div class='foo'>Content 3</div></body></html>";
        String result = processor.clearSelectors(html, ".foo");
        assertFalse(result.contains("Content 1"));
        assertFalse(result.contains("Content 3"));
        assertTrue(result.contains("Content 2"));
    }

    @Test
    void clearSelectorsRemovesElementsByIdTest() {
        String html = "<html><body><div id='remove-me'>Should be removed</div><div id='keep-me'>Should be kept</div></body></html>";
        String result = processor.clearSelectors(html, "#remove-me");
        assertFalse(result.contains("Should be removed"));
        assertTrue(result.contains("Should be kept"));
    }

    @Test
    void clearSelectorsRemovesMultipleElementsTest() {
        String html = "<html><body><span class='test'>A</span><span class='test'>B</span><span class='test'>C</span></body></html>";
        String result = processor.clearSelectors(html, ".test");
        assertFalse(result.contains(">A<"));
        assertFalse(result.contains(">B<"));
        assertFalse(result.contains(">C<"));
    }

    @Test
    void clearSelectorsWithComplexSelectorTest() {
        String html = "<html><body><div class='container'><p class='text'>Remove this</p><p>Keep this</p></div></body></html>";
        String result = processor.clearSelectors(html, "div.container p.text");
        assertFalse(result.contains("Remove this"));
        assertTrue(result.contains("Keep this"));
    }

    @Test
    void clearSelectorsWithNoMatchesTest() {
        String html = "<html><body><div>Content</div></body></html>";
        String result = processor.clearSelectors(html, ".non-existent");
        assertTrue(result.contains("Content"));
    }

    @Test
    void clearSelectorsRemovesNestedElementsTest() {
        String html = "<html><body><div class='outer'><div class='inner'>Nested content</div></div><div>Other content</div></body></html>";
        String result = processor.clearSelectors(html, ".outer");
        assertFalse(result.contains("Nested content"));
        assertTrue(result.contains("Other content"));
    }

    @Test
    void clearSelectorsWithAttributeSelectorTest() {
        String html = "<html><body><a href='http://example.com'>Link 1</a><a href='http://other.com'>Link 2</a></body></html>";
        String result = processor.clearSelectors(html, "a[href='http://example.com']");
        assertFalse(result.contains("Link 1"));
        assertTrue(result.contains("Link 2"));
    }

    @Test
    void clearSelectorsPreservesDocumentStructureTest() {
        String html = "<html><head><title>Test</title></head><body><div class='remove'>Text</div></body></html>";
        String result = processor.clearSelectors(html, ".remove");
        assertTrue(result.contains("<html"));
        assertTrue(result.contains("<head"));
        assertTrue(result.contains("<title>Test</title>"));
        assertFalse(result.contains("class=\"remove\""));
    }

    @Test
    void clearSelectorsWithMultipleSelectorsCombinedTest() {
        String html = "<html><body><div class='foo'>Remove 1</div><span id='bar'>Remove 2</span><p class='baz'>Remove 3</p><div>Keep this</div></body></html>";
        String result = processor.clearSelectors(html, ".foo, #bar, .baz");
        assertFalse(result.contains("Remove 1"));
        assertFalse(result.contains("Remove 2"));
        assertFalse(result.contains("Remove 3"));
        assertTrue(result.contains("Keep this"));
    }

    private ExportParams getExportParams() {
        return ExportParams.builder()
                .projectId("test_project")
                .language(Language.DE.name())
                .build();
    }
}
