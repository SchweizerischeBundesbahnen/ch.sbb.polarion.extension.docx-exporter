package ch.sbb.polarion.extension.docx_exporter.pandoc;

import ch.sbb.polarion.extension.docx_exporter.rest.model.conversion.ExportParams;
import ch.sbb.polarion.extension.docx_exporter.rest.model.documents.DocumentData;
import ch.sbb.polarion.extension.docx_exporter.rest.model.documents.id.LiveDocId;
import ch.sbb.polarion.extension.docx_exporter.util.DocumentDataFactory;
import com.polarion.alm.tracker.model.IModule;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;

/**
 * Every title heading of the document becomes a paragraph of the Title style, where it stands, and no chapter.
 * The name of the document never shows, since the LiveDoc does not show it either.
 */
@SkipTestWhenParamNotSet
class DocumentTitleTest extends BaseDocxConverterTest {

    private static final String DOCUMENT_NAME = "Name of the document";
    private static final String TITLE_STYLE = "Title";

    @Test
    @SneakyThrows
    void titleHeadingsBecomeTitleParagraphs() {
        List<DocxStructureInspector.Paragraph> paragraphs = DocxStructureInspector.paragraphs(export(
                "<h1 id=\"polarion_wiki macro name=module-workitem;params=id=EL-1\"><a id=\"work-item-anchor-elibrary/EL-1\"></a>Title &amp; heading</h1>"
                        + "<h1>One more title</h1>"
                        + "<h2>Chapter</h2>"
                        + "<p>Some content</p>"
                        + "<h1>Another title inside the body</h1>"));

        assertThat(paragraphs).extracting(DocxStructureInspector.Paragraph::styleId, DocumentTitleTest::text).containsExactly(
                tuple(TITLE_STYLE, "Title & heading"),
                tuple(TITLE_STYLE, "One more title"),
                tuple("Heading1", "Chapter"),
                tuple("BodyText", "Some content"),
                tuple(TITLE_STYLE, "Another title inside the body"));
    }

    @Test
    @SneakyThrows
    void documentWithoutTitleHeadingHasNoTitleParagraph() {
        List<DocxStructureInspector.Paragraph> paragraphs = DocxStructureInspector.paragraphs(export(
                "<h2>Chapter</h2><p>Some content</p>"));

        assertThat(paragraphs).noneMatch(paragraph -> TITLE_STYLE.equals(paragraph.styleId()));
        assertThat(paragraphs).noneMatch(paragraph -> text(paragraph).contains(DOCUMENT_NAME));
    }

    private byte[] export(String content) {
        ExportParams params = ExportParams.builder()
                .projectId("test")
                .locationPath("testLocation")
                .orientation("PORTRAIT")
                .paperSize("A4")
                .build();

        DocumentData<IModule> liveDoc = DocumentData.creator(module)
                .id(LiveDocId.from("testProjectId", "_default", "testDocumentId"))
                .title(DOCUMENT_NAME)
                .content(content)
                .lastRevision("1")
                .revisionPlaceholder("1")
                .build();
        documentDataFactoryMockedStatic.when(() ->
                DocumentDataFactory.getDocumentData(eq(params), anyBoolean())).thenReturn(liveDoc);

        return converter.convertToDocx(params);
    }

    private static String text(DocxStructureInspector.Paragraph paragraph) {
        return paragraph.segments().stream().map(DocxStructureInspector.Segment::text).collect(Collectors.joining());
    }
}
