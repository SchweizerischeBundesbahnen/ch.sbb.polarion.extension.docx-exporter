package ch.sbb.polarion.extension.docx_exporter.util;

import ch.sbb.polarion.extension.generic.context.CurrentContextExtension;
import ch.sbb.polarion.extension.generic.settings.NamedSettingsRegistry;
import ch.sbb.polarion.extension.docx_exporter.rest.model.conversion.ExportParams;
import ch.sbb.polarion.extension.docx_exporter.rest.model.documents.DocumentData;
import ch.sbb.polarion.extension.docx_exporter.rest.model.documents.id.DocumentProject;
import ch.sbb.polarion.extension.docx_exporter.rest.model.documents.id.LiveDocId;
import ch.sbb.polarion.extension.docx_exporter.rest.model.settings.filename.FileNameTemplateModel;
import ch.sbb.polarion.extension.docx_exporter.settings.FileNameTemplateSettings;
import ch.sbb.polarion.extension.docx_exporter.test_extensions.DocumentDataFactoryMockExtension;
import ch.sbb.polarion.extension.generic.test_extensions.PlatformContextMockExtension;
import ch.sbb.polarion.extension.generic.test_extensions.TransactionalExecutorExtension;
import ch.sbb.polarion.extension.docx_exporter.util.velocity.VelocityEvaluator;
import com.polarion.alm.tracker.model.IModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith({
        MockitoExtension.class,
        TransactionalExecutorExtension.class,
        PlatformContextMockExtension.class,
        CurrentContextExtension.class,
        DocumentDataFactoryMockExtension.class
})
class DocumentFileNameHelperTest {

    @Mock
    private VelocityEvaluator velocityEvaluator;

    @InjectMocks
    private DocumentFileNameHelper fileNameHelper;

    @InjectMocks
    private DocumentDataFactoryMockExtension documentDataFactoryMockExtension;

    @Test
    void evaluateVelocity() {
        DocumentData<IModule> documentData = DocumentData.creator(mock(IModule.class))
                .id(new LiveDocId(new DocumentProject("testProjectId", "Test Project"), "testSpaceId", "testDocumentId"))
                .title("Test Title")
                .lastRevision("12345")
                .revisionPlaceholder("12345")
                .build();
        FileNameTemplateModel settingOneModel = FileNameTemplateModel.builder()
                .documentNameTemplate("$projectName $document.moduleFolder $document.moduleName")
                .build();

        when(velocityEvaluator.evaluateVelocityExpressions(eq(documentData), anyString())).thenAnswer(a -> a.getArguments()[1]);

        String result = fileNameHelper.evaluateVelocity(documentData, settingOneModel.getDocumentNameTemplate());
        assertThat(result).endsWith(".docx");
    }

    @Test
    void getDocumentFileNameWithMissingProjectIdForLiveDocOrTestRun() {
        // Arrange
        ExportParams exportParams = ExportParams.builder()
                .projectId(null)
                .build();
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> fileNameHelper.getDocumentFileName(exportParams));
        assertEquals("Project ID must be provided for LiveDoc or TestRun export", exception.getMessage());
    }

    @Test
    void getDocumentFileName() {
        // Arrange
        ExportParams exportParams = ExportParams.builder()
                .projectId("testProjectId")
                .locationPath("testSpaceId/testDocumentId")
                .build();
        DocumentData<IModule> documentData = DocumentData.creator(mock(IModule.class))
                .id(new LiveDocId(new DocumentProject("testProjectId", "Test Project"), "testSpaceId", "testDocumentId"))
                .title("Test Title")
                .lastRevision("12345")
                .revisionPlaceholder("12345")
                .build();
        documentDataFactoryMockExtension.register(exportParams, documentData);

        FileNameTemplateSettings fileNameTemplateSettings = new FileNameTemplateSettings();
        FileNameTemplateSettings fileNameTemplateSettingsSpy = spy(fileNameTemplateSettings);
        doReturn(fileNameTemplateSettings.defaultValues()).when(fileNameTemplateSettingsSpy).read(anyString(), any(), any());

        NamedSettingsRegistry.INSTANCE.register(List.of(fileNameTemplateSettingsSpy));

        when(velocityEvaluator.evaluateVelocityExpressions(eq(documentData), anyString())).thenAnswer(a -> a.getArguments()[1]);

        // Act & Assert
        String documentFileName = fileNameHelper.getDocumentFileName(exportParams);
        assertThat(documentFileName).endsWith(".docx");
    }

    @Test
    void replaceIllegalFileNameSymbols() {
        assertThat(fileNameHelper.replaceIllegalFileNameSymbols("Space/test:86.docx")).isEqualTo("Space_test_86.docx");
    }
}
