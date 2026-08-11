package ch.sbb.polarion.extension.docx_exporter;

import ch.sbb.polarion.extension.docx_exporter.configuration.DocxExporterExtensionConfigurationExtension;
import ch.sbb.polarion.extension.generic.rest.model.Version;
import ch.sbb.polarion.extension.generic.test_extensions.PlatformContextMockExtension;
import ch.sbb.polarion.extension.generic.util.VersionUtils;
import com.polarion.alm.shared.api.SharedContext;
import com.polarion.alm.shared.api.utils.html.HtmlFragmentBuilder;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.tracker.model.IWorkItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The form extension contributes the fragment which imports the React side panel, and nothing else. What
 * the panel then offers is covered by the panel's own suite (ui/test/SidePanel*.test.tsx); what is asserted
 * here is that the fragment reaches the editor, addresses the bundle the build emits, and is contributed
 * only for a document.
 */
@ExtendWith({MockitoExtension.class, PlatformContextMockExtension.class, DocxExporterExtensionConfigurationExtension.class})
class DocxExporterFormExtensionTest {

    private final DocxExporterFormExtension extension = new DocxExporterFormExtension();

    @Test
    void testRenderFormContributesTheFragmentForADocument() {
        SharedContext context = mock(SharedContext.class, RETURNS_DEEP_STUBS);
        HtmlFragmentBuilder builder = mock(HtmlFragmentBuilder.class, RETURNS_DEEP_STUBS);
        when(context.createHtmlFragmentBuilderFor().gwt()).thenReturn(builder);

        extension.renderForm(context, mock(IModule.class, RETURNS_DEEP_STUBS));

        verify(builder).html(contains("id=\"docx-exporter-panel\""));
        verify(builder).finished();
    }

    @Test
    void testRenderFormContributesNothingForAnythingButADocument() {
        SharedContext context = mock(SharedContext.class, RETURNS_DEEP_STUBS);
        HtmlFragmentBuilder builder = mock(HtmlFragmentBuilder.class, RETURNS_DEEP_STUBS);
        when(context.createHtmlFragmentBuilderFor().gwt()).thenReturn(builder);

        extension.renderForm(context, mock(IWorkItem.class, RETURNS_DEEP_STUBS));

        verify(builder, never()).html(anyString());
        verify(builder).finished();
    }

    @Test
    void testFragmentMountsTheSidePanelBundle() {
        String fragment = extension.getSidePanelFragment();

        // The host the React app attaches its shadow root to, and the call that does it.
        assertTrue(fragment.contains("id=\"docx-exporter-panel\""));
        assertTrue(fragment.contains("assets/side-panel.js"));
        assertTrue(fragment.contains("module.mountSidePanel(\"#docx-exporter-panel\")"));
        // The trigger stylesheet whose onload fires that import; the panel's own styles are in the bundle.
        assertTrue(fragment.contains("ui/css/starter.css"));
    }

    @Test
    void testPaneIsLabelledAndCarriesNoIcon() {
        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);

        assertEquals("DOCX Exporter", extension.getLabel(module, null));
        assertNull(extension.getIcon(module, null));
    }

    @Test
    void testFragmentCarriesTheBundleVersion() {
        Version version = Version.builder().bundleVersion("13.5.1").build();
        try (MockedStatic<VersionUtils> versionUtils = mockStatic(VersionUtils.class)) {
            versionUtils.when(VersionUtils::getVersion).thenReturn(version);

            String fragment = extension.getSidePanelFragment();

            // The bundle is imported from a fixed URL, so the version is what busts the browser's cache of
            // it when the extension is updated.
            assertTrue(fragment.contains("side-panel.js?v=13.5.1"));
            assertFalse(fragment.contains("{BUNDLE_VERSION}"));
        }
    }

    @Test
    void testFragmentFallsBackWhenThereIsNoBundleVersion() {
        // No manifest to read it from - a unit test, or a deployment that lost its metadata. The
        // placeholder must still be substituted: left in the URL it would be requested literally.
        Version version = Version.builder().build();
        try (MockedStatic<VersionUtils> versionUtils = mockStatic(VersionUtils.class)) {
            versionUtils.when(VersionUtils::getVersion).thenReturn(version);

            String fragment = extension.getSidePanelFragment();

            assertTrue(fragment.contains("side-panel.js?v=0"));
            assertFalse(fragment.contains("{BUNDLE_VERSION}"));
        }
    }
}
