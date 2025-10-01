package ch.sbb.polarion.extension.docx_exporter.converter;

import ch.sbb.polarion.extension.docx_exporter.configuration.DocxExporterExtensionConfigurationExtension;
import ch.sbb.polarion.extension.docx_exporter.pandoc.service.PandocServiceConnector;
import ch.sbb.polarion.extension.docx_exporter.rest.model.conversion.ExportParams;
import ch.sbb.polarion.extension.docx_exporter.rest.model.documents.DocumentData;
import ch.sbb.polarion.extension.docx_exporter.rest.model.documents.id.LiveDocId;
import ch.sbb.polarion.extension.docx_exporter.rest.model.settings.templates.TemplatesModel;
import ch.sbb.polarion.extension.docx_exporter.service.DocxExporterPolarionService;
import ch.sbb.polarion.extension.docx_exporter.settings.TemplatesSettings;
import ch.sbb.polarion.extension.docx_exporter.util.DocumentDataFactory;
import ch.sbb.polarion.extension.docx_exporter.util.HtmlProcessor;
import ch.sbb.polarion.extension.docx_exporter.util.DocxTemplateProcessor;
import com.polarion.alm.tracker.internal.model.LinkRoleOpt;
import com.polarion.alm.tracker.internal.model.TypeOpt;
import com.polarion.alm.tracker.model.ILinkRoleOpt;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.tracker.model.ITrackerProject;
import com.polarion.alm.tracker.model.ITypeOpt;
import com.polarion.platform.persistence.IEnumeration;
import com.polarion.platform.persistence.spi.EnumOption;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Properties;

import static ch.sbb.polarion.extension.docx_exporter.pandoc.BasePandocTest.readTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, DocxExporterExtensionConfigurationExtension.class})
class DocxConverterTest {
    @Mock
    private DocxExporterPolarionService docxExporterPolarionService;
    @Mock
    private IModule module;
    @Mock
    private PandocServiceConnector pandocServiceConnector;
    @Mock
    private HtmlProcessor htmlProcessor;
    @Mock
    private TemplatesModel templatesModel;
    @Mock
    private DocxTemplateProcessor docxTemplateProcessor;

    private MockedStatic<DocumentDataFactory> documentDataFactoryMockedStatic;
    MockedConstruction<TemplatesSettings> templateSettingsMockedConstruction;

    @BeforeEach
    void setUp() {
        documentDataFactoryMockedStatic = mockStatic(DocumentDataFactory.class);
        templateSettingsMockedConstruction = mockConstruction(TemplatesSettings.class,
                (mock, context) -> when(mock.load(anyString(), any())).thenReturn(templatesModel));
    }

    @AfterEach
    void tearDown() {
        documentDataFactoryMockedStatic.close();
        templateSettingsMockedConstruction.close();
    }

    @Test
    @SneakyThrows
    void shouldConvertToDocxInSimplestWorkflow() {
        // Arrange
        ExportParams exportParams = ExportParams.builder()
                .projectId("testProjectId")
                .build();

        ITrackerProject project = mock(ITrackerProject.class);
        lenient().when(docxExporterPolarionService.getTrackerProject("testProjectId")).thenReturn(project);
        DocxConverter docxConverter = new DocxConverter(docxExporterPolarionService, pandocServiceConnector, htmlProcessor, docxTemplateProcessor);
        DocumentData<IModule> documentData = DocumentData.creator(module)
                .id(LiveDocId.from("testProjectId", "_default", "testDocumentId"))
                .title("testDocument")
                .content("test document content")
                .lastRevision("12345")
                .revisionPlaceholder("12345")
                .build();

        documentDataFactoryMockedStatic.when(() -> DocumentDataFactory.getDocumentData(eq(exportParams), anyBoolean())).thenReturn(documentData);
        when(docxTemplateProcessor.processUsing(eq("testDocument"), anyString())).thenReturn("test html content");
        when(pandocServiceConnector.convertToDocx("test html content", null)).thenReturn("test document content".getBytes());
        when(htmlProcessor.internalizeLinks(anyString())).thenAnswer(a -> a.getArgument(0));

        exportParams.setTemplate("testTemplate");
        when(templatesModel.getTemplate()).thenReturn(readTemplate("reference_template"));

        // Act
        byte[] result = docxConverter.convertToDocx(exportParams);

        // Assert
        assertThat(result).isEqualTo("test document content".getBytes());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldPostProcessDocumentContent() {
        // Arrange
        ExportParams exportParams = ExportParams.builder()
                .linkedWorkitemRoles(List.of("role1", "role2"))
                .build();
        ITrackerProject project = mock(ITrackerProject.class);
        IEnumeration<ILinkRoleOpt> roleEnum = mock(IEnumeration.class);
        Properties props1 = new Properties();
        props1.put("oppositeName", "testRole1OppositeName");
        ILinkRoleOpt option1 = new LinkRoleOpt(new EnumOption("roleLink", "role1", "role1", 1, false, props1));
        Properties props2 = new Properties();
        props2.put("oppositeName", "testRole2OppositeName");
        ILinkRoleOpt option2 = new LinkRoleOpt(new EnumOption("roleLink", "role2", "role2", 1, false, props2));
        when(roleEnum.getAvailableOptions("wiType")).thenReturn(List.of(option1, option2));
        IEnumeration<ITypeOpt> typeEnum = mock(IEnumeration.class);
        TypeOpt typeOption = new TypeOpt(new EnumOption("wiType", "wiType", "WIType", 1, false));
        when(typeEnum.getAllOptions()).thenReturn(List.of(typeOption));
        when(project.getWorkItemTypeEnum()).thenReturn(typeEnum);
        when(project.getWorkItemLinkRoleEnum()).thenReturn(roleEnum);
        when(htmlProcessor.processHtmlForPDF(anyString(), eq(exportParams), any(List.class))).thenReturn("result string");

        // Act
        DocxConverter docxConverter = new DocxConverter(null, null, htmlProcessor, null);
        String resultContent = docxConverter.postProcessDocumentContent(exportParams, project, "test content");

        // Assert
        assertThat(resultContent).isEqualTo("result string");
        ArgumentCaptor<List<String>> rolesCaptor = ArgumentCaptor.forClass(List.class);
        verify(htmlProcessor).processHtmlForPDF(eq("test content"), eq(exportParams), rolesCaptor.capture());
        assertThat(rolesCaptor.getValue()).containsExactly("role1", "testRole1OppositeName", "role2", "testRole2OppositeName");
    }

}
