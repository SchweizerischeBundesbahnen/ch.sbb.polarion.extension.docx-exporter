package ch.sbb.polarion.extension.docx_exporter.settings;

import ch.sbb.polarion.extension.docx_exporter.configuration.DocxExporterExtensionConfigurationExtension;
import ch.sbb.polarion.extension.docx_exporter.properties.DocxExporterExtensionConfiguration;
import ch.sbb.polarion.extension.docx_exporter.rest.model.settings.templates.TemplatesModel;
import ch.sbb.polarion.extension.generic.context.CurrentContextConfig;
import ch.sbb.polarion.extension.generic.context.CurrentContextExtension;
import ch.sbb.polarion.extension.generic.settings.SettingsService;
import ch.sbb.polarion.extension.generic.test_extensions.CustomExtensionMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith({CurrentContextExtension.class, DocxExporterExtensionConfigurationExtension.class})
@CurrentContextConfig("docx-exporter")
class TemplatesSettingsTest {

    private static final int DEFAULT_MAX_SIZE_MB = 16;

    @CustomExtensionMock
    private DocxExporterExtensionConfiguration configuration;

    private TemplatesSettings settings;

    @BeforeEach
    void setUp() {
        // Built here rather than in a field: the context these settings read is installed by the
        // extension, which runs after the test instance is constructed. The no-arg constructor would
        // additionally reach for the Polarion platform, which a unit test has no part of.
        settings = new TemplatesSettings(mock(SettingsService.class));
        when(configuration.getTemplateMaxSizeMB()).thenReturn(DEFAULT_MAX_SIZE_MB);
    }

    @Test
    void acceptsADocxOfAcceptableSize() {
        assertDoesNotThrow(() -> settings.validateTemplate(templateOf(docx(2048))));
    }

    @Test
    void acceptsAConfigurationCarryingNoTemplate() {
        assertDoesNotThrow(() -> settings.validateTemplate(TemplatesModel.builder().build()));
    }

    @Test
    void rejectsATemplateLargerThanTheConfiguredSize() {
        when(configuration.getTemplateMaxSizeMB()).thenReturn(1);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> settings.validateTemplate(templateOf(docx(2 * 1024 * 1024))));
        assertEquals("Template file must not exceed 1 MB", thrown.getMessage());
    }

    @Test
    void acceptsTheSameTemplateWhenTheConfiguredSizeAllowsIt() {
        when(configuration.getTemplateMaxSizeMB()).thenReturn(4);

        assertDoesNotThrow(() -> settings.validateTemplate(templateOf(docx(2 * 1024 * 1024))));
    }

    @Test
    void rejectsAFileThatIsNotAZipContainer() {
        byte[] notADocx = "this is a plain text file".getBytes(StandardCharsets.UTF_8);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> settings.validateTemplate(templateOf(notADocx)));
        assertEquals("Uploaded file must be a valid docx file", thrown.getMessage());
    }

    @Test
    void rejectsAFileTooShortToCarryTheSignature() {
        assertThrows(IllegalArgumentException.class, () -> settings.validateTemplate(templateOf(new byte[]{'P', 'K'})));
        assertThrows(IllegalArgumentException.class, () -> settings.validateTemplate(templateOf(new byte[0])));
    }

    /**
     * The size is checked before the signature, so an oversized file is named as such whatever it holds.
     */
    @Test
    void reportsTheSizeOfAnOversizedFileThatIsNotADocxEither() {
        when(configuration.getTemplateMaxSizeMB()).thenReturn(1);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> settings.validateTemplate(templateOf(new byte[2 * 1024 * 1024])));
        assertEquals("Template file must not exceed 1 MB", thrown.getMessage());
    }

    @Test
    void runsTheValidationBeforeEverySave() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> settings.beforeSave(templateOf(new byte[]{1, 2, 3, 4})));
        assertEquals("Uploaded file must be a valid docx file", thrown.getMessage());

        assertDoesNotThrow(() -> settings.beforeSave(templateOf(docx(2048))));
    }

    @Test
    void defaultsToNoTemplateAtAll() {
        assertNull(settings.defaultValues().getTemplate());
    }

    private static TemplatesModel templateOf(byte[] template) {
        return TemplatesModel.builder().template(template).build();
    }

    /** Bytes that open like a zip container, which is all the validation looks at. */
    private static byte[] docx(int size) {
        byte[] docx = new byte[size];
        byte[] signature = {'P', 'K', 3, 4};
        System.arraycopy(signature, 0, docx, 0, Math.min(signature.length, size));
        return Arrays.copyOf(docx, size);
    }
}
