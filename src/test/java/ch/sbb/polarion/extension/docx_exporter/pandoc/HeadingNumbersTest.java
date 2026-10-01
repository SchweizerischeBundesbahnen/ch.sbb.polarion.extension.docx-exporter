package ch.sbb.polarion.extension.docx_exporter.pandoc;

import ch.sbb.polarion.extension.docx_exporter.rest.model.conversion.ExportParams;
import ch.sbb.polarion.extension.docx_exporter.rest.model.documents.DocumentData;
import ch.sbb.polarion.extension.docx_exporter.rest.model.documents.id.LiveDocId;
import ch.sbb.polarion.extension.docx_exporter.rest.model.settings.templates.TemplatesModel;
import ch.sbb.polarion.extension.docx_exporter.settings.TemplatesSettings;
import ch.sbb.polarion.extension.docx_exporter.util.DocumentDataFactory;
import ch.sbb.polarion.extension.generic.test_extensions.BundleJarsPrioritizingRunnableMockExtension;
import com.polarion.alm.tracker.model.IModule;
import lombok.SneakyThrows;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

/**
 * Polarion writes the outline number of each heading into its text. The heading levels the template numbers by itself get the number from Word,
 * so Polarion's number is cut there and only there: otherwise Word would show it twice.
 */
@SkipTestWhenParamNotSet
// The template is processed by a BundleJarsPrioritizingRunnable, which needs the bundle of a running Polarion otherwise
@ExtendWith(BundleJarsPrioritizingRunnableMockExtension.class)
class HeadingNumbersTest extends BaseDocxConverterTest {

    private static final String TEMPLATE_NAME = "Numbered headings";

    /**
     * Three heading levels (Polarion's h2-h4) as Polarion renders a document with outline numbering on, the second heading without the
     * outline number mark, as Polarion renders some of them.
     */
    private static final String CONTENT = heading(2, "EL-1", true, "1", "Introduction")
            + "<p>Some content</p>"
            + heading(3, "EL-2", false, "1.1", "Purpose")
            + heading(4, "EL-3", true, "1.1.1", "Details")
            + heading(2, "EL-4", true, "2", "Overview");

    @Test
    @SneakyThrows
    void numbersOfLevelsNumberedByTemplateAreCut() {
        // The template numbers "heading 1" and "heading 2", not "heading 3"
        List<DocxStructureInspector.Paragraph> paragraphs = DocxStructureInspector.paragraphs(
                export(readTemplate("reference_template_numbered_headings")));

        assertThat(headings(paragraphs)).containsExactly(
                tuple("Heading1", "Introduction"),
                tuple("Heading2", "Purpose"),
                tuple("Heading3", "1.1.1 Details"),
                tuple("Heading1", "Overview"));
    }

    @Test
    @SneakyThrows
    void localizedTemplateIsRecognizedByStyleNames() {
        // The same template as Word saves it in German: the IDs of the heading styles are localized, their names are not
        List<DocxStructureInspector.Paragraph> paragraphs = DocxStructureInspector.paragraphs(
                export(readTemplate("reference_template_numbered_headings_de")));

        assertThat(headings(paragraphs)).containsExactly(
                tuple("berschrift1", "Introduction"),
                tuple("berschrift2", "Purpose"),
                tuple("berschrift3", "1.1.1 Details"),
                tuple("berschrift1", "Overview"));
    }

    @Test
    @SneakyThrows
    void numbersAreKeptWithTemplateNotNumberingHeadings() {
        List<DocxStructureInspector.Paragraph> paragraphs = DocxStructureInspector.paragraphs(
                export(readTemplate("reference_template")));

        assertThat(headings(paragraphs)).containsExactly(
                tuple("Heading1", "1 Introduction"),
                tuple("Heading2", "1.1 Purpose"),
                tuple("Heading3", "1.1.1 Details"),
                tuple("Heading1", "2 Overview"));
    }

    @Test
    @SneakyThrows
    void numbersAreKeptWithoutTemplate() {
        // pandoc then takes its default template, whose heading styles are not numbered
        List<DocxStructureInspector.Paragraph> paragraphs = DocxStructureInspector.paragraphs(export(null));

        assertThat(headings(paragraphs)).containsExactly(
                tuple("Heading1", "1 Introduction"),
                tuple("Heading2", "1.1 Purpose"),
                tuple("Heading3", "1.1.1 Details"),
                tuple("Heading1", "2 Overview"));
    }

    private byte[] export(byte @Nullable [] template) {
        ExportParams params = ExportParams.builder()
                .projectId("test")
                .locationPath("testLocation")
                .orientation("PORTRAIT")
                .paperSize("A4")
                .template(template == null ? null : TEMPLATE_NAME)
                .build();

        DocumentData<IModule> liveDoc = DocumentData.creator(module)
                .id(LiveDocId.from("testProjectId", "_default", "testDocumentId"))
                .title("Specification")
                .content(CONTENT)
                .lastRevision("1")
                .revisionPlaceholder("1")
                .build();
        documentDataFactoryMockedStatic.when(() ->
                DocumentDataFactory.getDocumentData(eq(params), anyBoolean())).thenReturn(liveDoc);

        try (MockedConstruction<TemplatesSettings> ignored = mockConstruction(TemplatesSettings.class,
                (settings, context) -> when(settings.load(any(), any())).thenReturn(TemplatesModel.builder().template(template).build()))) {
            return converter.convertToDocx(params);
        }
    }

    private static List<org.assertj.core.groups.Tuple> headings(List<DocxStructureInspector.Paragraph> paragraphs) {
        return paragraphs.stream()
                .filter(paragraph -> paragraph.styleId() != null && paragraph.styleId().matches("(Heading|berschrift)\\d"))
                .map(paragraph -> tuple(paragraph.styleId(), text(paragraph)))
                .toList();
    }

    private static String text(DocxStructureInspector.Paragraph paragraph) {
        // Polarion separates the number from the text with a non-breaking space
        return paragraph.segments().stream().map(DocxStructureInspector.Segment::text).collect(Collectors.joining())
                .replace(' ', ' ').replaceAll("\\s+", " ").trim();
    }

    private static String heading(int level, String workItemId, boolean marked, String number, String title) {
        return "<h" + level + " id=\"polarion_wiki macro name=module-workitem;params=id=" + workItemId + "\" title=\"Heading: " + workItemId + "\">"
                + "<a id=\"work-item-anchor-elibrary/" + workItemId + "\"></a>"
                + "<span " + (marked ? "id=\"polarion_editor_fields_container_start\" " : "") + "class=\"polarion-dle-workitem-fields-start\" contenteditable=\"false\">"
                + "<span " + (marked ? "id=\"polarion_editor_field=outlineNumber\" " : "") + "contenteditable=\"false\">" + number + "</span>&nbsp;</span>"
                + title + "</h" + level + ">";
    }
}
