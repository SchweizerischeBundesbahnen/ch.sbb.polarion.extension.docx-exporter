package ch.sbb.polarion.extension.docx_exporter.converter;

import ch.sbb.polarion.extension.docx_exporter.configuration.PdfExporterExtensionConfigurationExtension;
import ch.sbb.polarion.extension.docx_exporter.pandoc.service.PandocServiceConnector;
import ch.sbb.polarion.extension.docx_exporter.rest.model.conversion.ExportParams;
import ch.sbb.polarion.extension.docx_exporter.rest.model.documents.DocumentData;
import ch.sbb.polarion.extension.docx_exporter.rest.model.documents.id.LiveDocId;
import ch.sbb.polarion.extension.docx_exporter.service.PdfExporterPolarionService;
import ch.sbb.polarion.extension.docx_exporter.util.DocumentDataFactory;
import ch.sbb.polarion.extension.docx_exporter.util.HtmlProcessor;
import ch.sbb.polarion.extension.docx_exporter.util.PdfTemplateProcessor;
import ch.sbb.polarion.extension.docx_exporter.util.velocity.VelocityEvaluator;
import com.polarion.alm.tracker.internal.model.LinkRoleOpt;
import com.polarion.alm.tracker.internal.model.TypeOpt;
import com.polarion.alm.tracker.model.ILinkRoleOpt;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.tracker.model.ITrackerProject;
import com.polarion.alm.tracker.model.ITypeOpt;
import com.polarion.platform.persistence.IEnumeration;
import com.polarion.platform.persistence.spi.EnumOption;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.provider.Arguments;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, PdfExporterExtensionConfigurationExtension.class})
class PdfConverterTest {
    @Mock
    private PdfExporterPolarionService pdfExporterPolarionService;
    @Mock
    private IModule module;
    @Mock
    private VelocityEvaluator velocityEvaluator;
    @Mock
    private PandocServiceConnector pandocServiceConnector;
    @Mock
    private HtmlProcessor htmlProcessor;
    @Mock
    private PdfTemplateProcessor pdfTemplateProcessor;

    private MockedStatic<DocumentDataFactory> documentDataFactoryMockedStatic;

    @BeforeEach
    void setUp() {
        documentDataFactoryMockedStatic = mockStatic(DocumentDataFactory.class);
    }

    @AfterEach
    void tearDown() {
        documentDataFactoryMockedStatic.close();
    }

    @Test
    void shouldConvertToDocxInSimplestWorkflow() {
        // Arrange
        ExportParams exportParams = ExportParams.builder()
                .projectId("testProjectId")
                .build();

        ITrackerProject project = mock(ITrackerProject.class);
        lenient().when(pdfExporterPolarionService.getTrackerProject("testProjectId")).thenReturn(project);
        PdfConverter pdfConverter = new PdfConverter(pdfExporterPolarionService, pandocServiceConnector, htmlProcessor, pdfTemplateProcessor);
        DocumentData<IModule> documentData = DocumentData.creator(module)
                .id(LiveDocId.from("testProjectId", "_default", "testDocumentId"))
                .title("testDocument")
                .content("test document content")
                .lastRevision("12345")
                .revisionPlaceholder("12345")
                .build();

        documentDataFactoryMockedStatic.when(() -> DocumentDataFactory.getDocumentData(eq(exportParams), anyBoolean())).thenReturn(documentData);
        when(pdfTemplateProcessor.processUsing(eq("testDocument"), anyString())).thenReturn("test html content");
        when(pandocServiceConnector.convertToDocx("test html content")).thenReturn("test document content".getBytes());
        when(htmlProcessor.internalizeLinks(anyString())).thenAnswer(a -> a.getArgument(0));

        // Act
        byte[] result = pdfConverter.convertToPdf(exportParams);

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
        PdfConverter pdfConverter = new PdfConverter(null, null, htmlProcessor, null);
        String resultContent = pdfConverter.postProcessDocumentContent(exportParams, project, "test content");

        // Assert
        assertThat(resultContent).isEqualTo("result string");
        ArgumentCaptor<List<String>> rolesCaptor = ArgumentCaptor.forClass(List.class);
        verify(htmlProcessor).processHtmlForPDF(eq("test content"), eq(exportParams), rolesCaptor.capture());
        assertThat(rolesCaptor.getValue()).containsExactly("role1", "testRole1OppositeName", "role2", "testRole2OppositeName");
    }

    private static Stream<Arguments> paramsForGeneratePdf() {
        return Stream.of(
                Arguments.of(null),
                Arguments.of("internal content", "test cover page")
        );
    }
}
