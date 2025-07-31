package ch.sbb.polarion.extension.docx_exporter.rest.model.documents;

import ch.sbb.polarion.extension.generic.test_extensions.CustomExtensionMock;
import ch.sbb.polarion.extension.generic.test_extensions.TransactionalExecutorExtension;
import ch.sbb.polarion.extension.docx_exporter.rest.model.conversion.ExportParams;
import ch.sbb.polarion.extension.docx_exporter.rest.model.documents.id.LiveDocId;
import ch.sbb.polarion.extension.docx_exporter.util.exporter.ModifiedDocumentRenderer;
import com.polarion.alm.projects.model.IProject;
import com.polarion.alm.projects.model.IUniqueObject;
import com.polarion.alm.server.api.model.document.ProxyDocument;
import com.polarion.alm.shared.api.services.internal.InternalServices;
import com.polarion.alm.shared.api.transaction.internal.InternalReadOnlyTransaction;
import com.polarion.alm.tracker.ITrackerService;
import com.polarion.alm.tracker.model.IBaseline;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.tracker.model.IRichPage;
import com.polarion.alm.tracker.model.ITestRun;
import com.polarion.alm.tracker.model.ITrackerProject;
import com.polarion.alm.tracker.model.IWikiPage;
import com.polarion.alm.tracker.model.ipi.IInternalBaselinesManager;
import com.polarion.alm.tracker.spi.model.IInternalModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, TransactionalExecutorExtension.class})
class DocumentDataTest {

    @CustomExtensionMock
    private InternalReadOnlyTransaction internalReadOnlyTransactionMock;

    @Mock
    private IInternalBaselinesManager internalBaselinesManager;
    @Mock
    private IInternalModule module;
    @Mock
    private IRichPage richPageInDefaultRepo;
    @Mock
    private IRichPage richPageInProject;
    @Mock
    private ITestRun testRun;
    @Mock
    private IWikiPage wikiPageInDefaultRepo;
    @Mock
    private IWikiPage wikiPageInProject;

    @BeforeEach
    void setUp() {
        InternalServices internalServicesMock = mock(InternalServices.class);
        ITrackerService trackerServiceMock = mock(ITrackerService.class);
        ITrackerProject trackerProjectMock = mock(ITrackerProject.class);
        lenient().when(trackerProjectMock.getBaselinesManager()).thenReturn(internalBaselinesManager);
        lenient().when(trackerServiceMock.getTrackerProject((IProject) any())).thenReturn(trackerProjectMock);
        lenient().when(internalServicesMock.trackerService()).thenReturn(trackerServiceMock);
        lenient().when(internalReadOnlyTransactionMock.services()).thenReturn(internalServicesMock);

        mockDocumentBaseline(module);
        mockDocumentBaseline(richPageInDefaultRepo);
        mockDocumentBaseline(richPageInProject);
        mockDocumentBaseline(testRun);
        mockDocumentBaseline(wikiPageInDefaultRepo);
        mockDocumentBaseline(wikiPageInProject);
    }

    private void mockDocumentBaseline(IUniqueObject uniqueObject) {
        IBaseline documentBaselineName = mock(IBaseline.class);
        lenient().when(documentBaselineName.getName()).thenReturn("documentBaselineName");
        lenient().when(internalBaselinesManager.getRevisionBaseline(uniqueObject, "12345")).thenReturn(documentBaselineName);
        IBaseline projectBaselineName = mock(IBaseline.class);
        lenient().when(projectBaselineName.getName()).thenReturn("projectBaselineName");
        lenient().when(internalBaselinesManager.getRevisionBaseline("12345")).thenReturn(projectBaselineName);
    }

    @Test
    void testLiveDocDocumentData() {
        when(module.getId()).thenReturn("module id");
        when(module.getLastRevision()).thenReturn("12345");
        ITrackerProject project = mock(ITrackerProject.class);
        when(project.getId()).thenReturn("project id");
        when(project.getName()).thenReturn("project name");
        when(module.getProject()).thenReturn(project);
        when(module.getModuleFolder()).thenReturn("_default");
        when(module.getTitleOrName()).thenReturn("module title");

        UniqueObjectConverter uniqueObjectConverter = new UniqueObjectConverter(module);
        DocumentData<IModule> documentData = uniqueObjectConverter.toDocumentData();

        assertEquals("project id", documentData.getId().getDocumentProject().getId());
        assertEquals("project name", documentData.getId().getDocumentProject().getName());
        assertEquals("_default", ((LiveDocId) documentData.getId()).getSpaceId());
        assertEquals("module id", documentData.getId().getDocumentId());
        assertEquals("module title", documentData.getTitle());
        assertNull(documentData.getContent());
        assertNotNull(documentData.getBaseline());
        assertEquals("pb. projectBaselineName | db. documentBaselineName", documentData.getBaseline().asPlaceholder());
        assertNotNull(uniqueObjectConverter.getUniqueObjectAdapter().getDocumentBaseline());
        assertEquals("pb. projectBaselineName | db. documentBaselineName", uniqueObjectConverter.getUniqueObjectAdapter().getDocumentBaseline().asPlaceholder());
        assertNull(documentData.getRevision());
        assertEquals("12345", documentData.getLastRevision());
        assertNull(documentData.getContent());

        ExportParams exportParams = ExportParams.builder()
                .locationPath("location path")
                .build();

        try (
                MockedConstruction<ProxyDocument> proxyDocumentMockedConstruction = mockConstruction(ProxyDocument.class, (mock, context) -> {
                    when(mock.getHomePageContentHtml()).thenReturn("");
                });
                MockedConstruction<ModifiedDocumentRenderer> modifiedDocumentRendererMockedConstruction = mockConstruction(ModifiedDocumentRenderer.class, (mock, context) -> {
                    when(mock.render(anyString())).thenReturn("test content");
                })
        ) {
            DocumentData<IModule> documentDataWithContent = uniqueObjectConverter
                    .withContent(true)
                    .withExportParams(exportParams)
                    .toDocumentData();
            assertEquals("test content", documentDataWithContent.getContent());
        }
    }
}
