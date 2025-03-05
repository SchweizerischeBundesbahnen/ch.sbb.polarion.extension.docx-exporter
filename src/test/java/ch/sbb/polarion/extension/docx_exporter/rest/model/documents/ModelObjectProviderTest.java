package ch.sbb.polarion.extension.docx_exporter.rest.model.documents;

import ch.sbb.polarion.extension.generic.exception.ObjectNotFoundException;
import ch.sbb.polarion.extension.docx_exporter.rest.model.conversion.ExportParams;
import ch.sbb.polarion.extension.docx_exporter.service.PdfExporterPolarionService;
import ch.sbb.polarion.extension.generic.test_extensions.CustomExtensionMock;
import ch.sbb.polarion.extension.generic.test_extensions.TransactionalExecutorExtension;
import com.polarion.alm.projects.model.IProject;
import com.polarion.alm.shared.api.model.ModelObject;
import com.polarion.alm.shared.api.model.document.Document;
import com.polarion.alm.shared.api.model.document.DocumentSelector;
import com.polarion.alm.shared.api.model.document.internal.InternalDocuments;
import com.polarion.alm.shared.api.transaction.TransactionalExecutor;
import com.polarion.alm.shared.api.transaction.internal.InternalReadOnlyTransaction;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, TransactionalExecutorExtension.class})
class ModelObjectProviderTest {

    @CustomExtensionMock
    private InternalReadOnlyTransaction internalReadOnlyTransactionMock;

    @Mock
    private PdfExporterPolarionService pdfExporterPolarionService;

    @BeforeEach
    void setUp() {
        IProject projectMock = mock(IProject.class);
        lenient().when(projectMock.getId()).thenReturn("testProjectId");
        lenient().when(pdfExporterPolarionService.getProject(eq("testProjectId"))).thenReturn(projectMock);
        lenient().when(pdfExporterPolarionService.getProject(eq("nonExistingProjectId"))).thenThrow(new ObjectNotFoundException("Project not found"));
    }

    public static Stream<Arguments> paramsForModelObjectProviderGetDocument() {
        return Stream.of(
                Arguments.of(ExportParams.builder()
                        .projectId("testProjectId")
                        .locationPath("_default/testLocationPath")
                        .build()),
                Arguments.of(ExportParams.builder()
                        .projectId("testProjectId")
                        .locationPath("_default/testLocationPath")
                        .revision("12345")
                        .build())
        );
    }

    @ParameterizedTest
    @MethodSource("paramsForModelObjectProviderGetDocument")
    void testModelObjectProviderGetDocument(@NotNull ExportParams exportParams, ExtensionContext extensionContext) {
        InternalDocuments internalDocumentsMock = mock(InternalDocuments.class);
        DocumentSelector documentSelectorMock = mock(DocumentSelector.class);
        when(documentSelectorMock.revision(any())).thenReturn(documentSelectorMock);
        Document documentMock = mock(Document.class);
        when(documentSelectorMock.spaceReferenceAndName(any(), any())).thenReturn(documentMock);
        when(internalDocumentsMock.getBy()).thenReturn(documentSelectorMock);
        when(internalReadOnlyTransactionMock.documents()).thenReturn(internalDocumentsMock);

        ModelObjectProvider modelObjectProvider = new ModelObjectProvider(exportParams, pdfExporterPolarionService);
        ModelObject modelObject = TransactionalExecutor.executeSafelyInReadOnlyTransaction(modelObjectProvider::getModelObject);

        assertEquals(documentMock, modelObject);
    }

    public static Stream<Arguments> paramsForModelObjectProviderShouldFail() {
        return Stream.of(
                Arguments.of(ExportParams.builder()
                        .projectId("nonExistingProjectId")
                        .locationPath("_default/testLocationPath")
                        .build(), ObjectNotFoundException.class),
                Arguments.of(ExportParams.builder()
                        .projectId("testProjectId")
                        .build(), IllegalArgumentException.class),

                Arguments.of(ExportParams.builder()
                        .projectId("nonExistingProjectId")
                        .locationPath("_default/testLocationPath")
                        .build(), ObjectNotFoundException.class),
                Arguments.of(ExportParams.builder()
                        .projectId("testProjectId")
                        .build(), IllegalArgumentException.class),

                Arguments.of(ExportParams.builder()
                        .projectId("nonExistingProjectId")
                        .urlQueryParameters(Map.of("id", "testRunId"))
                        .build(), ObjectNotFoundException.class),
                Arguments.of(ExportParams.builder()
                        .projectId("testProjectId")
                        .build(), IllegalArgumentException.class),
                Arguments.of(ExportParams.builder()
                        .projectId("testProjectId")
                        .urlQueryParameters(Map.of())
                        .build(), IllegalArgumentException.class),

                Arguments.of(ExportParams.builder()
                        .projectId("nonExistingProjectId")
                        .locationPath("_default/testLocationPath")
                        .build(), ObjectNotFoundException.class),
                Arguments.of(ExportParams.builder()
                        .projectId("testProjectId")
                        .build(), IllegalArgumentException.class)
        );

    }

    @ParameterizedTest
    @MethodSource("paramsForModelObjectProviderShouldFail")
    void testModelObjectProviderShouldFail(@NotNull ExportParams exportParams, @NotNull Class<? extends Exception> expectedExceptionClass) {
        ModelObjectProvider modelObjectProvider = new ModelObjectProvider(exportParams, pdfExporterPolarionService);
        assertThrows(expectedExceptionClass, () -> TransactionalExecutor.executeSafelyInReadOnlyTransaction(modelObjectProvider::getModelObject));
    }
}
