package ch.sbb.polarion.extension.docx_exporter.rest;

import ch.sbb.polarion.extension.docx_exporter.settings.TemplatesSettings;
import ch.sbb.polarion.extension.generic.rest.GenericRestApplication;
import ch.sbb.polarion.extension.generic.settings.NamedSettingsRegistry;
import ch.sbb.polarion.extension.docx_exporter.converter.DocxConverterJobsCleaner;
import ch.sbb.polarion.extension.docx_exporter.rest.controller.CollectionApiController;
import ch.sbb.polarion.extension.docx_exporter.rest.controller.CollectionInternalController;
import ch.sbb.polarion.extension.docx_exporter.rest.controller.ConverterApiController;
import ch.sbb.polarion.extension.docx_exporter.rest.controller.ConverterInternalController;
import ch.sbb.polarion.extension.docx_exporter.rest.controller.SettingsApiController;
import ch.sbb.polarion.extension.docx_exporter.rest.controller.SettingsInternalController;
import ch.sbb.polarion.extension.docx_exporter.rest.controller.TestRunAttachmentsApiController;
import ch.sbb.polarion.extension.docx_exporter.rest.controller.TestRunAttachmentsInternalController;
import ch.sbb.polarion.extension.docx_exporter.rest.controller.UtilityResourcesApiController;
import ch.sbb.polarion.extension.docx_exporter.rest.controller.UtilityResourcesInternalController;
import ch.sbb.polarion.extension.docx_exporter.rest.exception.NoSuchElementExceptionMapper;
import ch.sbb.polarion.extension.docx_exporter.rest.exception.UnresolvableObjectExceptionMapper;
import ch.sbb.polarion.extension.docx_exporter.rest.exception.WrapperExceptionMapper;
import ch.sbb.polarion.extension.docx_exporter.rest.exception.XLIFFExceptionMapper;
import ch.sbb.polarion.extension.docx_exporter.rest.filter.ExportContextFilter;
import ch.sbb.polarion.extension.docx_exporter.settings.FileNameTemplateSettings;
import ch.sbb.polarion.extension.docx_exporter.settings.LocalizationSettings;
import ch.sbb.polarion.extension.docx_exporter.settings.StylePackageSettings;
import ch.sbb.polarion.extension.docx_exporter.settings.WebhooksSettings;
import com.polarion.core.util.logging.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Set;

public class DocxExporterRestApplication extends GenericRestApplication {
    private final Logger logger = Logger.getLogger(DocxExporterRestApplication.class);

    public DocxExporterRestApplication() {
        logger.debug("Creating Docx-Exporter REST Application...");

        try {
            NamedSettingsRegistry.INSTANCE.register(
                    Arrays.asList(
                            new StylePackageSettings(),
                            new TemplatesSettings(),
                            new LocalizationSettings(),
                            new WebhooksSettings(),
                            new FileNameTemplateSettings()
                    )
            );
        } catch (Exception e) {
            logger.error("Error during registration of named settings", e);
        }

        try {
            DocxConverterJobsCleaner.startCleaningJob();
        } catch (Exception e) {
            logger.error("Error during starting of clearing job", e);
        }

        logger.debug("Docx-Exporter REST Application has been created");
    }

    @Override
    protected @NotNull Set<Object> getExtensionControllerSingletons() {
        return Set.of(
                new ConverterApiController(),
                new ConverterInternalController(),
                new SettingsApiController(),
                new SettingsInternalController(),
                new TestRunAttachmentsApiController(),
                new TestRunAttachmentsInternalController(),
                new CollectionApiController(),
                new CollectionInternalController(),
                new UtilityResourcesApiController(),
                new UtilityResourcesInternalController()
        );
    }

    @Override
    protected @NotNull Set<Object> getExtensionExceptionMapperSingletons() {
        return Set.of(
                new XLIFFExceptionMapper(),
                new UnresolvableObjectExceptionMapper(),
                new WrapperExceptionMapper(),
                new NoSuchElementExceptionMapper()
        );
    }

    @Override
    protected @NotNull Set<Object> getExtensionFilterSingletons() {
        return Set.of(new ExportContextFilter());
    }
}
