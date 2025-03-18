package ch.sbb.polarion.extension.docx_exporter.service;

import ch.sbb.polarion.extension.generic.settings.NamedSettingsRegistry;
import ch.sbb.polarion.extension.generic.settings.SettingId;
import ch.sbb.polarion.extension.generic.settings.SettingName;
import ch.sbb.polarion.extension.generic.util.ScopeUtils;
import ch.sbb.polarion.extension.docx_exporter.rest.model.collections.DocumentCollectionEntry;
import ch.sbb.polarion.extension.docx_exporter.rest.model.settings.stylepackage.DocIdentifier;
import ch.sbb.polarion.extension.docx_exporter.rest.model.settings.stylepackage.StylePackageModel;
import ch.sbb.polarion.extension.docx_exporter.rest.model.settings.stylepackage.StylePackageWeightInfo;
import ch.sbb.polarion.extension.docx_exporter.settings.StylePackageSettings;
import com.polarion.alm.projects.IProjectService;
import com.polarion.alm.shared.api.model.baselinecollection.BaselineCollection;
import com.polarion.alm.shared.api.model.baselinecollection.BaselineCollectionReference;
import com.polarion.alm.shared.api.transaction.ReadOnlyTransaction;
import com.polarion.alm.tracker.ITestManagementService;
import com.polarion.alm.tracker.ITrackerService;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.tracker.model.ITrackerProject;
import com.polarion.alm.tracker.model.baselinecollection.IBaselineCollection;
import com.polarion.alm.tracker.model.baselinecollection.IBaselineCollectionElement;
import com.polarion.platform.IPlatformService;
import com.polarion.platform.security.ISecurityService;
import com.polarion.platform.service.repository.IRepositoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocxExporterPolarionServiceTest {

    private final ITrackerService trackerService = mock(ITrackerService.class);
    private final ITestManagementService testManagementService = mock(ITestManagementService.class);
    private StylePackageSettings stylePackageSettings;
    private final DocxExporterPolarionService service = new DocxExporterPolarionService(
            trackerService,
            mock(IProjectService.class),
            mock(ISecurityService.class),
            mock(IPlatformService.class),
            mock(IRepositoryService.class),
            testManagementService
    );

    @BeforeEach
    void setUp() {
        stylePackageSettings = mock(StylePackageSettings.class);
        when(stylePackageSettings.getFeatureName()).thenReturn("style-package");
        NamedSettingsRegistry.INSTANCE.getAll().clear();
        NamedSettingsRegistry.INSTANCE.register(List.of(stylePackageSettings));
    }

    @Test
    void testGetProjectFromScope_ValidScope() {
        String scope = "project/validProjectId/";
        String expectedProjectId = "validProjectId";
        ITrackerProject mockProject = mock(ITrackerProject.class);

        DocxExporterPolarionService polarionService = mock(DocxExporterPolarionService.class);
        when(polarionService.getProjectFromScope(anyString())).thenCallRealMethod();
        when(polarionService.getTrackerProject(anyString())).thenReturn(mockProject);

        try (MockedStatic<ScopeUtils> mockScopeUtils = mockStatic(ScopeUtils.class)) {
            mockScopeUtils.when(() -> ScopeUtils.getProjectFromScope(anyString())).thenReturn(expectedProjectId);

            ITrackerProject result = polarionService.getProjectFromScope(scope);

            assertNotNull(result);
            verify(polarionService, times(1)).getTrackerProject(expectedProjectId);
        }
    }

    @Test
    void testGetProjectFromScope_InvalidScope() {
        try (MockedStatic<ScopeUtils> mockScopeUtils = mockStatic(ScopeUtils.class)) {
            mockScopeUtils.when(() -> ScopeUtils.getProjectFromScope(anyString())).thenReturn(null);

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> service.getProjectFromScope("invalidScope"));

            assertEquals("Wrong scope format: invalidScope. Should be of form 'project/{projectId}/'", exception.getMessage());
        }
    }

    @Test
    void testGetStylePackagesWeights() {
        String scope = "project/someProjectId";
        Collection<SettingName> settingNames = List.of(
                SettingName.builder().id("id1").name("name1").scope(scope).build(),
                SettingName.builder().id("id2").name("name2").scope(scope).build()
        );
        StylePackageModel mockModel1 = mock(StylePackageModel.class);
        StylePackageModel mockModel2 = mock(StylePackageModel.class);

        when(stylePackageSettings.readNames(scope)).thenReturn(settingNames);
        when(stylePackageSettings.read(eq(scope), any(SettingId.class), isNull()))
                .thenReturn(mockModel1)
                .thenReturn(mockModel2);
        when(mockModel1.getWeight()).thenReturn(1.0f);
        when(mockModel2.getWeight()).thenReturn(2.0f);

        Collection<StylePackageWeightInfo> result = service.getStylePackagesWeights(scope);

        assertNotNull(result);
        assertEquals(2, result.size());
        verify(stylePackageSettings, times(2)).read(anyString(), any(SettingId.class), isNull());
    }

    @Test
    void testUpdateStylePackagesWeights() {
        List<StylePackageWeightInfo> weightInfos = new ArrayList<>();
        weightInfos.add(new StylePackageWeightInfo("Package1", "project/someProjectId", 1.0f));
        weightInfos.add(new StylePackageWeightInfo("Package2", "project/someProjectId", 2.0f));

        StylePackageModel mockModel = mock(StylePackageModel.class);

        when(stylePackageSettings.read(anyString(), any(SettingId.class), isNull())).thenReturn(mockModel);

        service.updateStylePackagesWeights(weightInfos);

        verify(stylePackageSettings, times(2)).read(anyString(), any(SettingId.class), isNull());
        verify(stylePackageSettings, times(2)).save(anyString(), any(SettingId.class), any(StylePackageModel.class));
    }

    @Test
    void testGetSuitableStylePackages() {
        Collection<SettingName> result = service.getSuitableStylePackages(List.of());
        assertNotNull(result);
        assertTrue(result.isEmpty());

        String projectId = "someProjectId";
        String spaceId = "someSpaceId";
        String documentName = "documentName";
        Collection<SettingName> defaultSettingNames = List.of(
                SettingName.builder().id("d1").name("default1").scope("").build(),
                SettingName.builder().id("d2").name("default2").scope("").build()
        );
        StylePackageModel defaultMockModel1 = mock(StylePackageModel.class);
        when(defaultMockModel1.getWeight()).thenReturn(10f);
        StylePackageModel defaultMockModel2 = mock(StylePackageModel.class);
        when(defaultMockModel2.getWeight()).thenReturn(16f);
        Collection<SettingName> settingNames = List.of(
                SettingName.builder().id("id1").name("name1").scope("project/someProjectId/").build(),
                SettingName.builder().id("id4").name("name4").scope("project/someProjectId/").build(),
                SettingName.builder().id("id2").name("name2").scope("project/someProjectId/").build(),
                SettingName.builder().id("id5").name("name5").scope("project/someProjectId/").build(),
                SettingName.builder().id("id3").name("name3").scope("project/someProjectId/").build()
        );
        StylePackageModel mockModel1 = mock(StylePackageModel.class);
        when(mockModel1.getWeight()).thenReturn(0.5f);
        StylePackageModel mockModel2 = mock(StylePackageModel.class);
        when(mockModel2.getWeight()).thenReturn(50.1f);
        StylePackageModel mockModel3 = mock(StylePackageModel.class);
        when(mockModel3.getWeight()).thenReturn(50.0f);
        StylePackageModel mockModel4 = mock(StylePackageModel.class);
        when(mockModel4.getWeight()).thenReturn(50.0f);
        StylePackageModel mockModel5 = mock(StylePackageModel.class);
        when(mockModel5.getWeight()).thenReturn(50.0f);

        when(stylePackageSettings.readNames("")).thenReturn(defaultSettingNames);
        when(stylePackageSettings.readNames(ScopeUtils.getScopeFromProject(projectId))).thenReturn(settingNames);
        when(stylePackageSettings.read(eq(""), eq(SettingId.fromName("default1")), isNull())).thenReturn(defaultMockModel1);
        when(stylePackageSettings.read(eq(""), eq(SettingId.fromName("default2")), isNull())).thenReturn(defaultMockModel2);
        when(stylePackageSettings.read(eq("project/someProjectId/"), eq(SettingId.fromName("name1")), isNull())).thenReturn(mockModel1);
        when(stylePackageSettings.read(eq("project/someProjectId/"), eq(SettingId.fromName("name2")), isNull())).thenReturn(mockModel2);
        when(stylePackageSettings.read(eq("project/someProjectId/"), eq(SettingId.fromName("name3")), isNull())).thenReturn(mockModel3);
        when(stylePackageSettings.read(eq("project/someProjectId/"), eq(SettingId.fromName("name4")), isNull())).thenReturn(mockModel4);
        when(stylePackageSettings.read(eq("project/someProjectId/"), eq(SettingId.fromName("name5")), isNull())).thenReturn(mockModel5);

        result = service.getSuitableStylePackages(List.of(new DocIdentifier(projectId, spaceId, documentName)));

        assertNotNull(result);
        assertEquals(5, result.size());
        assertEquals(List.of("name2", "name3", "name4", "name5", "name1"), result.stream().map(SettingName::getName).toList());

        result = service.getSuitableStylePackages(List.of(new DocIdentifier(projectId, spaceId, documentName), new DocIdentifier("anotherProject", spaceId, documentName)));
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(List.of("default2", "default1"), result.stream().map(SettingName::getName).toList());
    }

    @Test
    void testGetDocumentsFromCollection() {
        String projectId = "testProjectId";
        String collectionId = "testCollectionId";

        IBaselineCollection mockCollection = mock(IBaselineCollection.class);
        BaselineCollectionReference mockBaselineCollectionReference = mock(BaselineCollectionReference.class);

        IModule mockModule1 = mock(IModule.class);
        IModule mockModule2 = mock(IModule.class);

        when(mockModule1.getProjectId()).thenReturn(projectId);
        when(mockModule1.getModuleFolder()).thenReturn("space 1");
        when(mockModule1.getModuleName()).thenReturn("test Module1");
        when(mockModule1.getRevision()).thenReturn("1");

        when(mockModule2.getProjectId()).thenReturn(projectId);
        when(mockModule2.getModuleFolder()).thenReturn("_default");
        when(mockModule2.getModuleName()).thenReturn("test Module2");
        when(mockModule2.getRevision()).thenReturn("2");

        IBaselineCollectionElement mockElement1 = mock(IBaselineCollectionElement.class);
        IBaselineCollectionElement mockElement2 = mock(IBaselineCollectionElement.class);

        when(mockElement1.getObjectWithRevision()).thenReturn(mockModule1);
        when(mockElement2.getObjectWithRevision()).thenReturn(mockModule2);

        BaselineCollection baselineCollection = mock(BaselineCollection.class);
        when(mockCollection.getElements()).thenReturn(List.of(mockElement1, mockElement2));
        when(mockBaselineCollectionReference.get(Mockito.any())).thenReturn(baselineCollection);
        when(baselineCollection.getOldApi()).thenReturn(mockCollection);

        try (MockedConstruction<BaselineCollectionReference> mockedStaticReference = mockConstruction(BaselineCollectionReference.class, (mock, context) -> {
            when(mock.get(Mockito.any())).thenReturn(baselineCollection);
            when(mock.getWithRevision(Mockito.anyString())).thenReturn(mock);
        })) {
            List<DocumentCollectionEntry> result = service.getDocumentsFromCollection(projectId, collectionId, null, mock(ReadOnlyTransaction.class));

            assertNotNull(result);
            assertEquals(2, result.size());
            assertEquals("space 1", result.get(0).getSpaceId());
            assertEquals("test Module1", result.get(0).getDocumentName());
            assertEquals("1", result.get(0).getRevision());

            assertEquals("_default", result.get(1).getSpaceId());
            assertEquals("test Module2", result.get(1).getDocumentName());
            assertEquals("2", result.get(1).getRevision());

            List<DocumentCollectionEntry> resultWithRevision = service.getDocumentsFromCollection(projectId, collectionId, "1234", mock(ReadOnlyTransaction.class));

            assertNotNull(resultWithRevision);
            assertEquals(2, resultWithRevision.size());
            assertEquals("space 1", resultWithRevision.get(0).getSpaceId());
            assertEquals("test Module1", resultWithRevision.get(0).getDocumentName());
            assertEquals("1", resultWithRevision.get(0).getRevision());

            assertEquals("_default", resultWithRevision.get(1).getSpaceId());
            assertEquals("test Module2", resultWithRevision.get(1).getDocumentName());
            assertEquals("2", resultWithRevision.get(1).getRevision());

        }
    }

}
