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
import com.polarion.platform.persistence.IEnumOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
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
        StylePackageModel stylePackage = StylePackageModel.builder().build();
        doReturn(stylePackage).when(extension).getSelectedStylePackage(any(), any());
        doReturn(List.of(settingName)).when(extension).getSettingNames(any(), any());
        doReturn("TestFileName.docx").when(extension).getFilename(any());

        IEnumOption language = mock(IEnumOption.class);
        when(language.getId()).thenReturn("Deutsch");
        when(module.getCustomField("docLanguage")).thenReturn(language);

        try (MockedStatic<EnumValuesProvider> mockEnumValuesProvider = mockStatic(EnumValuesProvider.class)) {
            mockEnumValuesProvider.when(() -> EnumValuesProvider.getAllLinkRoleNames(any())).thenReturn(List.of("relates to", "blocks", "duplicates"));
            extension.renderForm(context, module);
            List<String> expectedEntries = Arrays.asList("someSpecificSelector", "<input id='render-comments' checked", "<input id='docx-orientation' checked", "<input id='docx-paper-size' checked");
            verify(builder, times(0)).html(argThat(arg -> expectedEntries.stream().allMatch(arg::contains)));

            stylePackage.setRemovalSelector("someSpecificSelector");
            stylePackage.setRenderComments(CommentsRenderType.ALL);
            stylePackage.setOrientation("LANDSCAPE");
            stylePackage.setPaperSize("A3");
            stylePackage.setLanguage("de");
            extension.renderForm(context, module);
            verify(builder, times(1)).html(argThat(arg -> expectedEntries.stream().allMatch(arg::contains)));
        }
    }

}
