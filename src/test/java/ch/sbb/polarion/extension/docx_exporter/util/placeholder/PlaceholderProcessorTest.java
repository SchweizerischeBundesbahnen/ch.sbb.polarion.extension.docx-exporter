package ch.sbb.polarion.extension.docx_exporter.util.placeholder;

import ch.sbb.polarion.extension.docx_exporter.service.DocxExporterPolarionService;
import com.polarion.alm.projects.IProjectService;
import com.polarion.alm.tracker.ITestManagementService;
import com.polarion.alm.tracker.ITrackerService;
import com.polarion.platform.IPlatformService;
import com.polarion.platform.core.PlatformContext;
import com.polarion.platform.security.ISecurityService;
import com.polarion.platform.service.repository.IRepositoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class PlaceholderProcessorTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    MockedStatic<PlatformContext> mockPlatformContext;

    private PlaceholderProcessor placeholderProcessor;
    private PlaceholderValues placeholderValues;

    @BeforeEach
    public void setup() {
        placeholderValues = PlaceholderValues.builder()
                .documentId("testDocId")
                .documentTitle("testDocTitle")
                .revision("testRevision")
                .productName("testProductName")
                .productVersion("testProductVersion")
                .timestamp("testTimestamp")
                .sbbCustomRevision("testCustomRevision")
                .projectName("testProjectName")
                .build();

        DocxExporterPolarionService docxExporterPolarionService = new DocxExporterPolarionService(
                mock(ITrackerService.class),
                mock(IProjectService.class),
                mock(ISecurityService.class),
                mock(IPlatformService.class),
                mock(IRepositoryService.class),
                mock(ITestManagementService.class)
        );
        placeholderProcessor = new PlaceholderProcessor(docxExporterPolarionService);
    }

    @Test
    void testTimestampReplaced() {
        final String text = "test text with {{ TIMESTAMP }}";
        final String result = placeholderProcessor.processPlaceholders(text, placeholderValues);
        assertTrue(result.contains("testTimestamp"));
    }

    @Test
    void testListReplaced() {
        final List<String> text = Arrays.asList("test text one with {{ TIMESTAMP }}", "test text two with {{ TIMESTAMP }}");
        final List<String> result = placeholderProcessor.processPlaceholders(text, placeholderValues);
        assertEquals(text.size(), result.size());
        assertTrue(result.get(0).contains("testTimestamp"));
        assertTrue(result.get(1).contains("testTimestamp"));
    }

    @Test
    void testNoPlaceholdersPresentNothingReplaced() {
        final String text = "test text with no placeholders";
        final String result = placeholderProcessor.processPlaceholders(text, placeholderValues);
        assertEquals(text, result);
    }

    @Test
    void testPlaceholderWithoutCurlyBracesPresentNothingReplaced() {
        final String text = "test text with correct placeholder text TIMESTAMP but no curly braces";
        final String result = placeholderProcessor.processPlaceholders(text, placeholderValues);
        assertEquals(text, result);
    }

    @ParameterizedTest
    @EmptySource
    void testEmptySource(String source) {
        final String result = placeholderProcessor.processPlaceholders(source, placeholderValues);
        assertTrue(result.isEmpty());
    }

    @AfterEach
    public void cleanup() {
        mockPlatformContext.close();
    }
}
