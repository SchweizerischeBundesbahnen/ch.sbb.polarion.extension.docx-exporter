package ch.sbb.polarion.extension.docx_exporter.util;

import ch.sbb.polarion.extension.docx_exporter.TestStringUtils;
import ch.sbb.polarion.extension.docx_exporter.rest.model.conversion.ExportParams;
import ch.sbb.polarion.extension.docx_exporter.rest.model.settings.localization.Language;
import ch.sbb.polarion.extension.docx_exporter.rest.model.settings.localization.LocalizationModel;
import ch.sbb.polarion.extension.docx_exporter.settings.LocalizationSettings;
import ch.sbb.polarion.extension.docx_exporter.util.html.HtmlLinksHelper;
import ch.sbb.polarion.extension.generic.settings.SettingId;
import ch.sbb.polarion.extension.generic.test_extensions.BundleJarsPrioritizingRunnableMockExtension;
import com.polarion.core.boot.PolarionProperties;
import lombok.SneakyThrows;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

@ExtendWith({MockitoExtension.class, BundleJarsPrioritizingRunnableMockExtension.class})
@SuppressWarnings("ConstantConditions")
class HtmlProcessorTest {

    @Mock
    private DocxExporterFileResourceProvider fileResourceProvider;
    @Mock
    private LocalizationSettings localizationSettings;
    @Mock
    private HtmlLinksHelper htmlLinksHelper;

    private HtmlProcessor processor;

    @BeforeEach
    void init() {
        processor = new HtmlProcessor(fileResourceProvider, localizationSettings, htmlLinksHelper);
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
        String img = "<img title=\"diagram_123.png\" src=\"data:image/png;BASE64Content\" />";
        String imgInsideA = String.format("<a href=\"http://localhost/polarion/module-attachment/elibrary/some-path\">%s</a>", img);
        Document document = JSoupUtils.parseHtml(imgInsideA);
        processor.cutLocalUrls(document);
        assertEquals(img, document.body().html());

        String span = "<span id=\"PLANID_Version_1_0\" title=\"Version 1.0 (Version_1_0) (2017-03-31)\" class=\"polarion-Plan\">Version 1.0<span> (2017-03-31)</span></span>";
        String spanInsideA = String.format("<a href=\"http://localhost/polarion/#/project/elibrary/another-path\">%s</a>", span);
        document = JSoupUtils.parseHtml(spanInsideA);
        processor.cutLocalUrls(document);
        assertEquals(span, document.body().html());
    }

    @Test
    @SneakyThrows
    void cutLocalUrlsWithRolesFilteringTest() {
        when(localizationSettings.load(any(), any(SettingId.class))).thenReturn(new LocalizationModel(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap()));

        try (InputStream isInvalidHtml = this.getClass().getResourceAsStream("/cutLocalUrlsWithRolesFilteringBeforeProcessing.html");
             InputStream isValidHtml = this.getClass().getResourceAsStream("/cutLocalUrlsWithRolesFilteringAfterProcessing.html")) {

            ExportParams exportParams = getExportParams();
            exportParams.setCutLocalUrls(true);

            String invalidHtml = new String(isInvalidHtml.readAllBytes(), StandardCharsets.UTF_8);

            List<String> selectedRoles = Arrays.asList("has parent", "is parent of", "depends on", "blocks", "verifies", "is verified by");
            // Spaces and new lines are removed to exclude difference in space characters
            String fixedHtml = processor.processHtmlForExport(invalidHtml, exportParams, selectedRoles);
            String validHtml = new String(isValidHtml.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(TestStringUtils.removeNonsensicalSymbols(validHtml), TestStringUtils.removeNonsensicalSymbols(fixedHtml));
        }
    }

    @Test
    @SneakyThrows
    void rewritePolarionUrlsTest() {
        String anchor = "<a id=\"work-item-anchor-testProject/12345\"></a>";
        String link = "<a href=\"http://localhost/polarion/#/project/testProject/workitem?id=12345\">Work Item 12345</a>";
        String htmlWithAnchor = anchor + link;
        String expected = anchor + "<a href=\"#work-item-anchor-testProject/12345\">Work Item 12345</a>";

        Document document = JSoupUtils.parseHtml(htmlWithAnchor);
        processor.rewritePolarionUrls(document);
        assertEquals(expected, document.body().html());

        String wikiLink = "<a href=\"http://localhost/polarion/#/project/testProject/wiki/Specification/LinkedWorkItems?selection=12345\">Work Item 12345</a>";
        String htmlWithWikiAnchor = anchor + wikiLink;
        document = JSoupUtils.parseHtml(htmlWithWikiAnchor);
        processor.rewritePolarionUrls(document);
        assertEquals(expected, document.body().html());

        document = JSoupUtils.parseHtml(link);
        processor.rewritePolarionUrls(document);
        assertEquals(link, document.body().html());

        link = "<a href=\"http://localhost/polarion/#/project/testProject\">Work Item 12345</a>";
        document = JSoupUtils.parseHtml(link);
        assertEquals(link, document.body().html());

        link = "<a href=\"http://localhost/polarion/testProject\">Work Item 12345</a>";
        document = JSoupUtils.parseHtml(link);
        assertEquals(link, document.body().html());
    }

    @Test
    @SneakyThrows
    void cutEmptyChaptersTest() {
        try (InputStream isInvalidHtml = this.getClass().getResourceAsStream("/emptyChaptersBeforeProcessing.html");
             InputStream isValidHtml = this.getClass().getResourceAsStream("/emptyChaptersAfterProcessing.html")) {

            Document invalidDocument = JSoupUtils.parseHtml(new String(isInvalidHtml.readAllBytes(), StandardCharsets.UTF_8));

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

            Document document = JSoupUtils.parseHtml(new String(isInvalidHtml.readAllBytes(), StandardCharsets.UTF_8));

            processor.fixTableHeads(document);
            String fixedHtml = document.body().html();
            String validHtml = new String(isValidHtml.readAllBytes(), StandardCharsets.UTF_8);

            // Spaces and new lines are removed to exclude difference in space characters
            assertEquals(TestStringUtils.removeNonsensicalSymbols(validHtml), TestStringUtils.removeNonsensicalSymbols(fixedHtml));
        }
    }

    @Test
    @SneakyThrows
    void adjustImageAlignmentTest() {
        try (InputStream isInvalidHtml = this.getClass().getResourceAsStream("/imageAlignmentBeforeProcessing.html");
             InputStream isValidHtml = this.getClass().getResourceAsStream("/imageAlignmentAfterProcessing.html")) {

            Document document = JSoupUtils.parseHtml(new String(isInvalidHtml.readAllBytes(), StandardCharsets.UTF_8));

            processor.adjustImageAlignment(document);
            String fixedHtml = document.body().html();
            String validHtml = new String(isValidHtml.readAllBytes(), StandardCharsets.UTF_8);

            // Spaces and new lines are removed to exclude difference in space characters
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

            Document document = JSoupUtils.parseHtml(new String(isInvalidHtml.readAllBytes(), StandardCharsets.UTF_8));

            processor.cutNotNeededChapters(document, List.of("3", "4", "7"));
            String fixedHtml = document.body().html();
            String validHtml = new String(isValidHtml.readAllBytes(), StandardCharsets.UTF_8);

            // Spaces and new lines are removed to exclude difference in space characters
            assertEquals(TestStringUtils.removeNonsensicalSymbols(validHtml), TestStringUtils.removeNonsensicalSymbols(fixedHtml));
        }
    }

    @Test
    @SneakyThrows
    void removePageBreakAvoidsTest() {
        try (InputStream isInvalidHtml = this.getClass().getResourceAsStream("/withPageBreakAvoids.html");
             InputStream isValidHtml = this.getClass().getResourceAsStream("/withoutPageBreakAvoids.html")) {

            Document document = JSoupUtils.parseHtml(new String(isInvalidHtml.readAllBytes(), StandardCharsets.UTF_8));

            processor.removePageBreakAvoids(document);
            String fixedHtml = document.body().html();
            String validHtml = new String(isValidHtml.readAllBytes(), StandardCharsets.UTF_8);

            // Spaces and new lines are removed to exclude difference in space characters
            assertEquals(TestStringUtils.removeNonsensicalSymbols(validHtml), TestStringUtils.removeNonsensicalSymbols(fixedHtml));
        }
    }

    @Test
    @SneakyThrows
    void fixNumberedListsTest() {
        try (InputStream isInvalidHtml = this.getClass().getResourceAsStream("/invalidNumberedLists.html");
             InputStream isValidHtml = this.getClass().getResourceAsStream("/validNumberedLists.html")) {

            Document document = JSoupUtils.parseHtml(new String(isInvalidHtml.readAllBytes(), StandardCharsets.UTF_8));

            processor.fixNestedLists(document);
            String fixedHtml = document.body().html();
            String validHtml = new String(isValidHtml.readAllBytes(), StandardCharsets.UTF_8);

            // Spaces and new lines are removed to exclude difference in space characters
            assertEquals(TestStringUtils.removeNonsensicalSymbols(validHtml), TestStringUtils.removeNonsensicalSymbols(fixedHtml));
        }
    }

    @Test
    @SneakyThrows
    void localizeEnumsTest() {
        try (InputStream isInvalidHtml = this.getClass().getResourceAsStream("/localizeEnumsBeforeProcessing.html");
             InputStream isValidHtml = this.getClass().getResourceAsStream("/localizeEnumsAfterProcessing.html")) {

            Document document = JSoupUtils.parseHtml(new String(isInvalidHtml.readAllBytes(), StandardCharsets.UTF_8));

            processor.localizeEnums(document, getExportParams());
            String fixedHtml = document.body().html();
            String validHtml = new String(isValidHtml.readAllBytes(), StandardCharsets.UTF_8);

            // Spaces and new lines are removed to exclude difference in space characters
            assertEquals(TestStringUtils.removeNonsensicalSymbols(validHtml), TestStringUtils.removeNonsensicalSymbols(fixedHtml));
        }
    }

    @Test
    @SneakyThrows
    void selectLinkedWorkItemTypesTableTest() {
        try (InputStream isInvalidHtml = this.getClass().getResourceAsStream("/linkedWorkItemsTableBeforeProcessing.html");
             InputStream isValidHtml = this.getClass().getResourceAsStream("/linkedWorkItemsTableAfterProcessing.html")) {

            String invalidHtml = new String(isInvalidHtml.readAllBytes(), StandardCharsets.UTF_8);

            ExportParams exportParams = getExportParams();
            exportParams.setLinkedWorkitemRoles(List.of("has parent"));

            List<String> selectedRoleEnumValues = Arrays.asList("has parent", "is parent of");

            // Spaces and new lines are removed to exclude difference in space characters
            String fixedHtml = processor.processHtmlForExport(invalidHtml, exportParams, selectedRoleEnumValues);
            String validHtml = new String(isValidHtml.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(TestStringUtils.removeNonsensicalSymbols(validHtml), TestStringUtils.removeNonsensicalSymbols(fixedHtml));
        }
    }

    @Test
    @SneakyThrows
    void selectLinkedWorkItemTypesTest() {
        try (InputStream isInvalidHtml = this.getClass().getResourceAsStream("/linkedWorkItemsBeforeProcessing.html");
             InputStream isValidHtml = this.getClass().getResourceAsStream("/linkedWorkItemsAfterProcessing.html")) {

            String invalidHtml = new String(isInvalidHtml.readAllBytes(), StandardCharsets.UTF_8);

            ExportParams exportParams = getExportParams();
            exportParams.setLinkedWorkitemRoles(List.of("has parent"));

            List<String> selectedRoleEnumValues = Arrays.asList("has parent", "is parent of");

            // Spaces and new lines are removed to exclude difference in space characters
            String fixedHtml = processor.processHtmlForExport(invalidHtml, exportParams, selectedRoleEnumValues);
            String validHtml = new String(isValidHtml.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(TestStringUtils.removeNonsensicalSymbols(validHtml), TestStringUtils.removeNonsensicalSymbols(fixedHtml));
        }
    }

    @Test
    @SneakyThrows
    void malformedLinkedWorkItemTypesTest() {
        try (InputStream isInvalidHtml = this.getClass().getResourceAsStream("/malformedLinkedWorkItemsBeforeProcessing.html");
             InputStream isValidHtml = this.getClass().getResourceAsStream("/malformedLinkedWorkItemsAfterProcessing.html")) {

            Document document = JSoupUtils.parseHtml(new String(isInvalidHtml.readAllBytes(), StandardCharsets.UTF_8));

            List<String> selectedRoleEnumValues = Arrays.asList("has parent", "is parent of");

            processor.filterNonTabularLinkedWorkItems(document, selectedRoleEnumValues);
            String fixedHtml = document.body().html();
            String validHtml = new String(isValidHtml.readAllBytes(), StandardCharsets.UTF_8);

            // Spaces and new lines are removed to exclude difference in space characters
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
        try (InputStream is = new ByteArrayInputStream("<?xml version=\"1.0\" encoding=\"UTF-8\"?><svg xmlns=\"http://www.w3.org/2000/svg\"><switch><g requiredFeatures=\"http://www.w3.org/TR/SVG11/feature#Extensibility\"/></switch></svg>".getBytes(StandardCharsets.UTF_8))) {
            imgBytes = is.readAllBytes();
        }
        when(fileResourceProvider.getResourceAsBytes(any())).thenReturn(imgBytes);
        when(fileResourceProvider.getResourceAsBase64String(any())).thenCallRealMethod();
        String result = processor.replaceResourcesAsBase64Encoded(html);
        String base64SvgImage = Base64.getEncoder().encodeToString("<?xml version=\"1.0\" encoding=\"UTF-8\"?><svg xmlns=\"http://www.w3.org/2000/svg\"><switch><g requiredFeatures=\"http://www.w3.org/TR/SVG11/feature#Extensibility\"/></switch></svg>".getBytes(StandardCharsets.UTF_8));
        String expected = "<div><img id=\"image1\" src=\"data:image/svg+xml;base64," + base64SvgImage + "\"/> " +
                "<img id='image2' src='data:image/svg+xml;base64," + base64SvgImage + "'/> " +
                "<img id='image1' src='data:image/svg+xml;base64," + base64SvgImage + "'/></div>";
        assertEquals(expected, result);
    }

    @Test
    @SneakyThrows
    void processHtmlForExportTestCutEmptyWorkItemAttributesDisabled() {
        try (InputStream isHtml = this.getClass().getResourceAsStream("/emptyWIAttributesBeforeProcessing.html")) {

            String html = new String(isHtml.readAllBytes(), StandardCharsets.UTF_8);

            HtmlProcessor spyHtmlProcessor = spy(processor);
            ExportParams exportParams = getExportParams();
            // to avoid changing input html and check with regular equals
            doNothing().when(spyHtmlProcessor).adjustCellWidth(any());
            exportParams.setCutEmptyChapters(false);

            // Spaces, new lines & nbsp symbols are removed to exclude difference in space characters
            String result = spyHtmlProcessor.processHtmlForExport(html, exportParams, List.of());
            assertEquals(TestStringUtils.removeNonsensicalSymbols(html.replaceAll("&nbsp;|\u00A0", " ")), TestStringUtils.removeNonsensicalSymbols(result));
        }
    }

    @ParameterizedTest
    @CsvSource({
            "<h1>First level heading</h1>, <div class=\"title\">First level heading</div>",
            "<h2>Second level heading</h2>, <h1>Second level heading</h1>",
            "<h3>Third level heading</h3>, <h2>Third level heading</h2>"
    })
    void adjustHeadingForExportTest(String inputHtml, String expectedHtml) {
        String result = processor.processHtmlForExport(inputHtml, getExportParams(), List.of());
        assertEquals(expectedHtml, result);
    }

    @Test
    void replaceDollars() {
        String html = "<div>100$</div>";
        String result = processor.processHtmlForExport(html, getExportParams(), List.of());
        assertEquals("<div>100&dollar;</div>", result);
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
    void tableOfContent() {
        try (
                InputStream isInitialHtml = this.getClass().getResourceAsStream("/tableOfContentBeforeProcessing.html");
                InputStream isExpectedHtml = this.getClass().getResourceAsStream("/tableOfContentAfterProcessing.html")
        ) {
            String initialHtml = new String(isInitialHtml.readAllBytes(), StandardCharsets.UTF_8);
            String expectedHtml = new String(isExpectedHtml.readAllBytes(), StandardCharsets.UTF_8);

            LiveDocTOCGenerator liveDocTOCGenerator = new LiveDocTOCGenerator();

            Document document = JSoupUtils.parseHtml(initialHtml);

            liveDocTOCGenerator.addTableOfContent(document);
            String processedHtml = document.body().html();

            // Spaces and new lines are removed to exclude difference in space characters
            assertEquals(TestStringUtils.removeNonsensicalSymbols(expectedHtml), TestStringUtils.removeNonsensicalSymbols(processedHtml));
        }
    }

    @Test
    @SneakyThrows
    void tableOfTablesTest() {
        try (InputStream isInvalidHtml = this.getClass().getResourceAsStream("/tableOfTablesBeforeProcessing.html");
             InputStream isValidHtml = this.getClass().getResourceAsStream("/tableOfTablesAfterProcessing.html")) {

            Document document = JSoupUtils.parseHtml(new String(isInvalidHtml.readAllBytes(), StandardCharsets.UTF_8));

            processor.addTableOfFigures(document);
            String fixedHtml = document.body().html();
            String validHtml = new String(isValidHtml.readAllBytes(), StandardCharsets.UTF_8);

            // Spaces and new lines are removed to exclude difference in space characters
            assertEquals(TestStringUtils.removeNonsensicalSymbols(validHtml), TestStringUtils.removeNonsensicalSymbols(fixedHtml));
        }
    }

    @Test
    @SneakyThrows
    void tableOfFiguresTest() {
        try (InputStream isInvalidHtml = this.getClass().getResourceAsStream("/tableOfFiguresBeforeProcessing.html");
             InputStream isValidHtml = this.getClass().getResourceAsStream("/tableOfFiguresAfterProcessing.html")) {

            Document document = JSoupUtils.parseHtml(new String(isInvalidHtml.readAllBytes(), StandardCharsets.UTF_8));

            processor.addTableOfFigures(document);
            String fixedHtml = document.body().html();
            String validHtml = new String(isValidHtml.readAllBytes(), StandardCharsets.UTF_8);

            // Spaces and new lines are removed to exclude difference in space characters
            assertEquals(TestStringUtils.removeNonsensicalSymbols(validHtml), TestStringUtils.removeNonsensicalSymbols(fixedHtml));
        }
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
            Document document = JSoupUtils.parseHtml(html);
            processor.replaceLinks(document);
            String result = document.body().html();
            assertTrue(result.contains("href=\"http://example.com/base/page.html\""));
        } finally {
            System.setProperty(PolarionProperties.BASE_URL, "");
        }
    }

    @ParameterizedTest
    @CsvSource({
            "<a href=\"https://external.com/page\">External</a>",
            "<a href=\"mailto:someone@example.com\">Email</a>",
            "<a href=\"#dlecaption_123\">Caption</a>"
    })
    void ignoreNonRelativeLinksTest(String html) {
        Document document = JSoupUtils.parseHtml(html);
        processor.replaceLinks(document);
        String result = document.body().html();
        assertEquals(html, result);
    }

    @Test
    void malformedHrefIsSafeTest() {
        String baseUrl = "http://example.com/base/";
        System.setProperty(PolarionProperties.BASE_URL, baseUrl);
        try {
            String html = "<a href=\"..//page\">Broken path</a>";
            Document document = JSoupUtils.parseHtml(html);
            processor.replaceLinks(document);
            String result = document.body().html();
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

    @ParameterizedTest
    @CsvSource({
            "http://example.com/base/, foo/bar.html, http://example.com/base/foo/bar.html",
            "http://example.com, /polarion/#/project/MyProject/wiki/Common/My Document Title, http://example.com/polarion/#/project/MyProject/wiki/Common/My%20Document%20Title",
            "http://example.com, /path/to/page?query=value, http://example.com/path/to/page?query=value"
    })
    void testResolveUrl(String baseUrl, String relativeUrl, String expectedUrl) {
        String result = processor.resolveUrl(baseUrl, relativeUrl);
        assertEquals(expectedUrl, result);
    }

    @Test
    void testResolveUrlWithInvalidBaseUrlReturnsOriginal() {
        // Invalid base URL with unescaped characters triggers URISyntaxException
        String invalidBaseUrl = "http://example.com/path with spaces/";
        String relativeUrl = "page.html";
        String result = processor.resolveUrl(invalidBaseUrl, relativeUrl);
        // When URISyntaxException is caught, the original relativeUrl is returned
        assertEquals(relativeUrl, result);
    }

    @Test
    void processHtmlForExportWithRemovalSelectorTest() {
        String html = "<html><body><div class='remove-me'>Should be removed</div><div class='keep-me'>Should be kept</div></body></html>";
        ExportParams exportParams = getExportParams();
        exportParams.setRemovalSelector(".remove-me");

        String result = processor.processHtmlForExport(html, exportParams, List.of());

        assertFalse(result.contains("Should be removed"));
        assertTrue(result.contains("Should be kept"));
    }

    @Test
    void clearSelectorsRemovesMatchingElementsTest() {
        String html = "<html><body><div class='foo'>Content 1</div><div class='bar'>Content 2</div><div class='foo'>Content 3</div></body></html>";
        Document document = JSoupUtils.parseHtml(html);
        processor.clearSelectors(document, ".foo");
        String result = document.html();
        assertFalse(result.contains("Content 1"));
        assertFalse(result.contains("Content 3"));
        assertTrue(result.contains("Content 2"));
    }

    @Test
    void clearSelectorsRemovesElementsByIdTest() {
        String html = "<html><body><div id='remove-me'>Should be removed</div><div id='keep-me'>Should be kept</div></body></html>";
        Document document = JSoupUtils.parseHtml(html);
        processor.clearSelectors(document, "#remove-me");
        String result = document.html();
        assertFalse(result.contains("Should be removed"));
        assertTrue(result.contains("Should be kept"));
    }

    @Test
    void clearSelectorsRemovesMultipleElementsTest() {
        String html = "<html><body><span class='test'>A</span><span class='test'>B</span><span class='test'>C</span></body></html>";
        Document document = JSoupUtils.parseHtml(html);
        processor.clearSelectors(document, ".test");
        String result = document.html();
        assertFalse(result.contains(">A<"));
        assertFalse(result.contains(">B<"));
        assertFalse(result.contains(">C<"));
    }

    @Test
    void clearSelectorsWithComplexSelectorTest() {
        String html = "<html><body><div class='container'><p class='text'>Remove this</p><p>Keep this</p></div></body></html>";
        Document document = JSoupUtils.parseHtml(html);
        processor.clearSelectors(document, "div.container p.text");
        String result = document.html();
        assertFalse(result.contains("Remove this"));
        assertTrue(result.contains("Keep this"));
    }

    @Test
    void clearSelectorsWithNoMatchesTest() {
        String html = "<html><body><div>Content</div></body></html>";
        Document document = JSoupUtils.parseHtml(html);
        processor.clearSelectors(document, ".non-existent");
        String result = document.html();
        assertTrue(result.contains("Content"));
    }

    @Test
    void clearSelectorsRemovesNestedElementsTest() {
        String html = "<html><body><div class='outer'><div class='inner'>Nested content</div></div><div>Other content</div></body></html>";
        Document document = JSoupUtils.parseHtml(html);
        processor.clearSelectors(document, ".outer");
        String result = document.html();
        assertFalse(result.contains("Nested content"));
        assertTrue(result.contains("Other content"));
    }

    @Test
    void clearSelectorsWithAttributeSelectorTest() {
        String html = "<html><body><a href='http://example.com'>Link 1</a><a href='http://other.com'>Link 2</a></body></html>";
        Document document = JSoupUtils.parseHtml(html);
        processor.clearSelectors(document, "a[href='http://example.com']");
        String result = document.html();
        assertFalse(result.contains("Link 1"));
        assertTrue(result.contains("Link 2"));
    }

    @Test
    void clearSelectorsPreservesDocumentStructureTest() {
        String html = "<html><head><title>Test</title></head><body><div class='remove'>Text</div></body></html>";
        Document document = JSoupUtils.parseHtml(html);
        processor.clearSelectors(document, ".remove");
        String result = document.html();
        assertTrue(result.contains("<html"));
        assertTrue(result.contains("<head"));
        assertTrue(result.contains("<title>Test</title>"));
        assertFalse(result.contains("class=\"remove\""));
    }

    @Test
    void clearSelectorsWithMultipleSelectorsCombinedTest() {
        String html = "<html><body><div class='foo'>Remove 1</div><span id='bar'>Remove 2</span><p class='baz'>Remove 3</p><div>Keep this</div></body></html>";
        Document document = JSoupUtils.parseHtml(html);
        processor.clearSelectors(document, ".foo, #bar, .baz");
        String result = document.body().html();
        assertFalse(result.contains("Remove 1"));
        assertFalse(result.contains("Remove 2"));
        assertFalse(result.contains("Remove 3"));
        assertTrue(result.contains("Keep this"));
    }

    @Test
    void fixTableHeadRowspanSimpleTest() {
        String html = """
                <table>
                    <thead>
                        <tr>
                            <th rowspan="3">Column 1</th>
                            <th rowspan="2">Column 2</th>
                            <th>Column 3</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr>
                            <td>Data 1</td>
                            <td>Data 2</td>
                        </tr>
                        <tr>
                            <td>Data 3</td>
                            <td>Data 4</td>
                        </tr>
                        <tr>
                            <td>Data 5</td>
                            <td>Data 6</td>
                        </tr>
                    </tbody>
                </table>
                """;

        Document document = Jsoup.parse(html);
        processor.fixTableHeadRowspan(document);

        // After fixing: thead should have 3 rows (1 original + 2 moved from tbody)
        assertEquals(3, document.select("thead tr").size());
        // tbody should have 1 row left
        assertEquals(1, document.select("tbody tr").size());
    }

    @Test
    void fixTableHeadRowspanNoRowspanTest() {
        String html = """
                <table>
                    <thead>
                        <tr>
                            <th>Column 1</th>
                            <th>Column 2</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr>
                            <td>Data 1</td>
                            <td>Data 2</td>
                        </tr>
                    </tbody>
                </table>
                """;

        Document document = Jsoup.parse(html);
        processor.fixTableHeadRowspan(document);

        // Should remain unchanged
        assertEquals(1, document.select("thead tr").size());
        assertEquals(1, document.select("tbody tr").size());
    }

    @Test
    void fixTableHeadRowspanNoTheadTest() {
        String html = """
                <table>
                    <tbody>
                        <tr>
                            <td rowspan="2">Data 1</td>
                            <td>Data 2</td>
                        </tr>
                        <tr>
                            <td>Data 3</td>
                        </tr>
                    </tbody>
                </table>
                """;

        Document document = Jsoup.parse(html);
        processor.fixTableHeadRowspan(document);

        // Should remain unchanged (no thead)
        assertEquals(0, document.select("thead").size());
        assertEquals(2, document.select("tbody tr").size());
    }

    @Test
    void fixTableHeadRowspanWithColspanTest() {
        String html = """
                <table>
                    <thead>
                        <tr>
                            <th rowspan="2" colspan="2">Header 1</th>
                            <th>Header 2</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr>
                            <td>Data 1</td>
                            <td>Data 2</td>
                        </tr>
                        <tr>
                            <td>Data 3</td>
                            <td>Data 4</td>
                        </tr>
                    </tbody>
                </table>
                """;

        Document document = Jsoup.parse(html);
        processor.fixTableHeadRowspan(document);

        // After fixing: thead should have 2 rows (1 original + 1 moved from tbody)
        assertEquals(2, document.select("thead tr").size());
        // tbody should have 1 row left
        assertEquals(1, document.select("tbody tr").size());
    }

    @Test
    void fixTableHeadRowspanMultipleTablesTest() {
        String html = """
                <table>
                    <thead>
                        <tr>
                            <th rowspan="2">Header 1</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr><td>Data 1</td></tr>
                        <tr><td>Data 2</td></tr>
                    </tbody>
                </table>
                <table>
                    <thead>
                        <tr>
                            <th rowspan="3">Header 2</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr><td>Data 3</td></tr>
                        <tr><td>Data 4</td></tr>
                        <tr><td>Data 5</td></tr>
                    </tbody>
                </table>
                """;

        Document document = Jsoup.parse(html);
        processor.fixTableHeadRowspan(document);

        // Both tables should be processed
        assertEquals(2, document.select("table").size());
        // First table thead should have 2 rows, tbody should have 1 row
        assertEquals(2, document.select("table").get(0).select("thead tr").size());
        assertEquals(1, document.select("table").get(0).select("tbody tr").size());
        // Second table thead should have 3 rows, tbody should have 1 row
        assertEquals(3, document.select("table").get(1).select("thead tr").size());
        assertEquals(1, document.select("table").get(1).select("tbody tr").size());
    }

    @Test
    void fixTableHeadRowspanNoTbodyTest() {
        String html = """
                <table>
                    <thead>
                        <tr>
                            <th rowspan="2">Header 1</th>
                            <th>Header 2</th>
                        </tr>
                    </thead>
                </table>
                """;

        Document document = Jsoup.parse(html);
        processor.fixTableHeadRowspan(document);

        // Should remain unchanged (no tbody to move rows from)
        assertEquals(1, document.select("thead tr").size());
    }

    @Test
    void fixTableHeadRowspanInsufficientRowsTest() {
        String html = """
                <table>
                    <thead>
                        <tr>
                            <th rowspan="5">Header 1</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr><td>Data 1</td></tr>
                        <tr><td>Data 2</td></tr>
                    </tbody>
                </table>
                """;

        Document document = Jsoup.parse(html);
        processor.fixTableHeadRowspan(document);

        // Should move only available rows (2 rows) even though rowspan is 5
        assertEquals(3, document.select("thead tr").size()); // 1 original + 2 moved
        assertEquals(0, document.select("tbody tr").size()); // all rows moved
    }

    private ExportParams getExportParams() {
        return ExportParams.builder()
                .projectId("test_project")
                .language(Language.DE.name())
                .build();
    }
}
