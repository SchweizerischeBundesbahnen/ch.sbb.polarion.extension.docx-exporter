package ch.sbb.polarion.extension.docx_exporter.pandoc;

import ch.sbb.polarion.extension.docx_exporter.converter.DocxConverter;
import ch.sbb.polarion.extension.docx_exporter.pandoc.service.PandocServiceConnector;
import ch.sbb.polarion.extension.docx_exporter.rest.model.settings.localization.LocalizationModel;
import ch.sbb.polarion.extension.docx_exporter.service.DocxExporterPolarionService;
import ch.sbb.polarion.extension.docx_exporter.settings.LocalizationSettings;
import ch.sbb.polarion.extension.docx_exporter.util.DocumentDataFactory;
import ch.sbb.polarion.extension.docx_exporter.util.DocxTemplateProcessor;
import ch.sbb.polarion.extension.docx_exporter.util.FileResourceProvider;
import ch.sbb.polarion.extension.docx_exporter.util.HtmlProcessor;
import ch.sbb.polarion.extension.docx_exporter.util.html.HtmlLinksHelper;
import ch.sbb.polarion.extension.docx_exporter.util.placeholder.PlaceholderProcessor;
import ch.sbb.polarion.extension.docx_exporter.util.velocity.VelocityEvaluator;
import com.polarion.alm.projects.IProjectService;
import com.polarion.alm.projects.model.IProject;
import com.polarion.alm.tracker.ITestManagementService;
import com.polarion.alm.tracker.ITrackerService;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.tracker.model.ITrackerProject;
import com.polarion.platform.IPlatformService;
import com.polarion.platform.security.ISecurityService;
import com.polarion.platform.service.repository.IRepositoryService;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Base class for DOCX converter integration tests.
 * Provides common setup, teardown, and utility methods for testing DOCX conversion functionality.
 */
@ExtendWith(MockitoExtension.class)
public abstract class BaseDocxConverterTest extends BasePandocTest {

    protected MockedStatic<DocumentDataFactory> documentDataFactoryMockedStatic;

    protected DocxExporterPolarionService docxExporterPolarionService;
    protected PandocServiceConnector pandocServiceConnector;
    protected DocxTemplateProcessor docxTemplateProcessor;
    protected PlaceholderProcessor placeholderProcessor;
    protected HtmlProcessor htmlProcessor;
    protected VelocityEvaluator velocityEvaluator;
    protected DocxConverter converter;
    protected LocalizationSettings localizationSettings;
    protected IModule module;

    @BeforeEach
    protected void setUp() {
        prepareBaseMocks();
        documentDataFactoryMockedStatic = mockStatic(DocumentDataFactory.class);
        setupConverter();
    }

    @AfterEach
    protected void tearDown() {
        if (documentDataFactoryMockedStatic != null) {
            documentDataFactoryMockedStatic.close();
        }
    }

    /**
     * Prepares base mocks that are common for all DOCX converter tests.
     */
    @SneakyThrows
    protected void prepareBaseMocks() {
        ITrackerService trackerService = mock(ITrackerService.class);
        IProjectService projectService = mock(IProjectService.class);

        docxExporterPolarionService = new DocxExporterPolarionService(
                trackerService,
                projectService,
                mock(ISecurityService.class),
                mock(IPlatformService.class),
                mock(IRepositoryService.class),
                mock(ITestManagementService.class)
        );

        IProject project = mock(IProject.class);
        when(projectService.getProject(anyString())).thenReturn(project);
        ITrackerProject trackerProject = mock(ITrackerProject.class);
        when(trackerService.getTrackerProject(any(IProject.class))).thenReturn(trackerProject);
        lenient().when(trackerProject.isUnresolvable()).thenReturn(false);

        module = mock(IModule.class);
        pandocServiceConnector = getPandocServiceConnector();

        setupBasicSettings();
        setupHelperComponents();
    }

    protected void setupBasicSettings() {
        localizationSettings = mock(LocalizationSettings.class);
        when(localizationSettings.load(any(), any())).thenReturn(new LocalizationModel(null, null, null));
    }

    /**
     * Sets up helper components (placeholder processor, velocity evaluator, etc.).
     */
    protected void setupHelperComponents() {
        placeholderProcessor = new PlaceholderProcessor(docxExporterPolarionService);
        velocityEvaluator = mock(VelocityEvaluator.class);
        docxTemplateProcessor = new DocxTemplateProcessor(velocityEvaluator, placeholderProcessor);

        HtmlLinksHelper htmlLinksHelper = mock(HtmlLinksHelper.class);
        when(htmlLinksHelper.internalizeLinks(anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        FileResourceProvider fileResourceProvider = mock(FileResourceProvider.class);
        htmlProcessor = new HtmlProcessor(fileResourceProvider, localizationSettings, htmlLinksHelper);
    }

    /**
     * Sets up the DOCX converter with all required dependencies.
     */
    protected void setupConverter() {
        converter = new DocxConverter(
                docxExporterPolarionService,
                pandocServiceConnector,
                htmlProcessor,
                docxTemplateProcessor
        );
    }
}
