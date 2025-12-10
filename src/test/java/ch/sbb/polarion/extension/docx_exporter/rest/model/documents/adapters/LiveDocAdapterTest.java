package ch.sbb.polarion.extension.docx_exporter.rest.model.documents.adapters;

import ch.sbb.polarion.extension.docx_exporter.rest.model.conversion.CommentsRenderType;
import ch.sbb.polarion.extension.docx_exporter.rest.model.conversion.ExportParams;
import ch.sbb.polarion.extension.docx_exporter.util.LiveDocCommentsProcessor;
import ch.sbb.polarion.extension.docx_exporter.util.WorkItemCommentsProcessor;
import ch.sbb.polarion.extension.docx_exporter.util.exporter.ModifiedDocumentRenderer;
import com.polarion.alm.server.api.model.document.ProxyDocument;
import com.polarion.alm.shared.api.transaction.internal.InternalReadOnlyTransaction;
import com.polarion.alm.tracker.model.IModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LiveDocAdapterTest {

    private final String renderedContent = "<p>Rendered Content</p>";
    private MockedConstruction<ProxyDocument> proxyDocumentMockedConstruction;
    private MockedConstruction<ModifiedDocumentRenderer> documentRendererMockedConstruction;
    private MockedConstruction<WorkItemCommentsProcessor> workItemCommentsProcessorMockedConstruction;
    private MockedConstruction<LiveDocCommentsProcessor> liveDocCommentsProcessorMockedConstruction;

    @BeforeEach
    void setUp() {
        proxyDocumentMockedConstruction = mockConstruction(ProxyDocument.class, (mock, context) -> {
        });

        workItemCommentsProcessorMockedConstruction = mockConstruction(WorkItemCommentsProcessor.class,
                (mock, context) -> when(mock.addWorkItemComments(any(), anyString(), anyBoolean())).thenAnswer(invocation -> invocation.getArgument(1)));
        liveDocCommentsProcessorMockedConstruction = mockConstruction(LiveDocCommentsProcessor.class,
                (mock, context) -> when(mock.addLiveDocComments(any(), anyString(), nullable(CommentsRenderType.class))).thenAnswer(invocation -> invocation.getArgument(1)));
        documentRendererMockedConstruction = mockConstruction(ModifiedDocumentRenderer.class,
                (mock, context) -> when(mock.render(nullable(String.class))).thenAnswer(invocation -> renderedContent));
    }

    @AfterEach
    void tearDown() {
        proxyDocumentMockedConstruction.close();
        documentRendererMockedConstruction.close();
        workItemCommentsProcessorMockedConstruction.close();
        liveDocCommentsProcessorMockedConstruction.close();
    }

    @Test
    void getContentTest() {
        LiveDocAdapter adapter = new LiveDocAdapter(mock(IModule.class));
        InternalReadOnlyTransaction transaction = mock(InternalReadOnlyTransaction.class);

        ExportParams exportParams = ExportParams.builder().build();
        assertDoesNotThrow(() -> adapter.getContent(exportParams, transaction));

        exportParams.setInternalContent("some content");
        assertDoesNotThrow(() -> adapter.getContent(exportParams, transaction));

        exportParams.setRenderComments(CommentsRenderType.OPEN);
        assertDoesNotThrow(() -> adapter.getContent(exportParams, transaction));

        exportParams.setRenderComments(CommentsRenderType.ALL);
        assertDoesNotThrow(() -> adapter.getContent(exportParams, transaction));
    }

}
