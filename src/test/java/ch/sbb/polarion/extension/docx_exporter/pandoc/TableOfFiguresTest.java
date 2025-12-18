package ch.sbb.polarion.extension.docx_exporter.pandoc;

import ch.sbb.polarion.extension.docx_exporter.rest.model.conversion.ExportParams;
import ch.sbb.polarion.extension.docx_exporter.rest.model.documents.DocumentData;
import ch.sbb.polarion.extension.docx_exporter.rest.model.documents.id.LiveDocId;
import ch.sbb.polarion.extension.docx_exporter.util.DocumentDataFactory;
import com.polarion.alm.tracker.model.IModule;
import lombok.SneakyThrows;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.nio.file.Files;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;

@SkipTestWhenParamNotSet
@SuppressWarnings("ResultOfMethodCallIgnored")
class TableOfFiguresTest extends BaseDocxConverterTest {
    private static Stream<Arguments> provideTableOfFiguresTestCases() {
        return Stream.of(
                Arguments.of("tableOfFigures", "Table of Figures Test"),
                Arguments.of("tableOfTables", "Table of Tables Test"),
                Arguments.of("tableOfFiguresAndTables", "Combined ToF and ToT Test")
        );
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("provideTableOfFiguresTestCases")
    @SneakyThrows
    void testTableOfFiguresGeneration(String htmlResource, String title) {
        ExportParams params = ExportParams.builder()
                .projectId("test")
                .locationPath("testLocation")
                .orientation("PORTRAIT")
                .paperSize("A4")
                .build();

        DocumentData<IModule> liveDoc = DocumentData.creator(module)
                .id(LiveDocId.from("testProjectId", "_default", "testDocumentId"))
                .title(title)
                .content(readHtmlResource(htmlResource))
                .lastRevision("1")
                .revisionPlaceholder("1")
                .build();
        documentDataFactoryMockedStatic.when(() -> DocumentDataFactory.getDocumentData(eq(params), anyBoolean())).thenReturn(liveDoc);

        byte[] doc = converter.convertToDocx(params);
        assertNotNull(doc);
        writeReportDocx(htmlResource, doc);
        File newFile = getTestFile();
        try {
            Files.write(newFile.toPath(), doc);
            byte[] pdf = exportToPDF(newFile);
            assertNotNull(pdf);
            compareContentUsingReferenceImages(htmlResource, pdf);
        } finally {
            newFile.delete();
        }
    }
}
