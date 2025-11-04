package ch.sbb.polarion.extension.docx_exporter;

import ch.sbb.polarion.extension.docx_exporter.configuration.DocxExporterExtensionConfigurationExtension;
import ch.sbb.polarion.extension.docx_exporter.rest.model.conversion.CommentsRenderType;
import ch.sbb.polarion.extension.docx_exporter.rest.model.settings.stylepackage.StylePackageModel;
import ch.sbb.polarion.extension.docx_exporter.util.EnumValuesProvider;
import ch.sbb.polarion.extension.generic.settings.SettingName;
import ch.sbb.polarion.extension.generic.test_extensions.PlatformContextMockExtension;
import com.polarion.alm.shared.api.SharedContext;
import com.polarion.alm.shared.api.utils.html.HtmlFragmentBuilder;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.tracker.model.ITrackerProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, PlatformContextMockExtension.class, DocxExporterExtensionConfigurationExtension.class})
class DocxExporterFormExtensionTest {

    @Test
    void testRenderForm() {
        SharedContext context = mock(SharedContext.class, RETURNS_DEEP_STUBS);
        HtmlFragmentBuilder builder = mock(HtmlFragmentBuilder.class, RETURNS_DEEP_STUBS);
        when(context.createHtmlFragmentBuilderFor().gwt()).thenReturn(builder);
        IModule module = mock(IModule.class);
        ITrackerProject project = mock(ITrackerProject.class);
        when(project.getId()).thenReturn("testProjectId");
        when(module.getProject()).thenReturn(project);

        DocxExporterFormExtension extension = spy(new DocxExporterFormExtension());
        SettingName settingName = SettingName.builder().id("testId").name("testName").build();
        doReturn(List.of(settingName)).when(extension).getSuitableStylePackages(any());
        StylePackageModel stylePackage = StylePackageModel.builder()
                .removalSelector("someSpecificSelector")
                .renderComments(CommentsRenderType.ALL)
                .orientation("LANDSCAPE")
                .paperSize("A3")
                .build();
        doReturn(stylePackage).when(extension).getSelectedStylePackage(any(), any());
        doReturn(List.of(settingName)).when(extension).getSettingNames(any(), any());
        doReturn("TestFileName.docx").when(extension).getFilename(any());

        try (MockedStatic<EnumValuesProvider> mockEnumValuesProvider = mockStatic(EnumValuesProvider.class)) {
            mockEnumValuesProvider.when(() -> EnumValuesProvider.getAllLinkRoleNames(any())).thenReturn(List.of("relates to", "blocks", "duplicates"));
            extension.renderForm(context, module);
            verify(builder).html(argThat(arg -> arg != null && arg.contains("someSpecificSelector")));
        }
    }

}
