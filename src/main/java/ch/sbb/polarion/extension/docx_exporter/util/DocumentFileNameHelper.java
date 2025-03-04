package ch.sbb.polarion.extension.docx_exporter.util;

import ch.sbb.polarion.extension.generic.settings.NamedSettings;
import ch.sbb.polarion.extension.generic.settings.NamedSettingsRegistry;
import ch.sbb.polarion.extension.generic.settings.SettingId;
import ch.sbb.polarion.extension.generic.util.ScopeUtils;
import ch.sbb.polarion.extension.docx_exporter.rest.model.conversion.ExportParams;
import ch.sbb.polarion.extension.docx_exporter.rest.model.documents.DocumentData;
import ch.sbb.polarion.extension.docx_exporter.rest.model.settings.filename.FileNameTemplateModel;
import ch.sbb.polarion.extension.docx_exporter.settings.FileNameTemplateSettings;
import ch.sbb.polarion.extension.docx_exporter.util.placeholder.PlaceholderProcessor;
import ch.sbb.polarion.extension.docx_exporter.util.velocity.VelocityEvaluator;
import com.polarion.alm.projects.model.IUniqueObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

public class DocumentFileNameHelper {

    private final VelocityEvaluator velocityEvaluator;

    public DocumentFileNameHelper() {
        this.velocityEvaluator = new VelocityEvaluator();
    }

    public DocumentFileNameHelper(VelocityEvaluator velocityEvaluator) {
        this.velocityEvaluator = velocityEvaluator;
    }

    public String getDocumentFileName(@NotNull ExportParams exportParams) {
        if (exportParams.getProjectId() == null) {
            throw new IllegalArgumentException("Project ID must be provided for LiveDoc or TestRun export");
        }

        final DocumentData<? extends IUniqueObject> documentData = DocumentDataFactory.getDocumentData(exportParams, false);

        FileNameTemplateModel fileNameTemplateModel = getFileNameTemplateModel(ScopeUtils.getScopeFromProject(exportParams.getProjectId()));
        @NotNull String fileNameTemplate = getFileNameTemplate(fileNameTemplateModel);
        fileNameTemplate = new PlaceholderProcessor().replacePlaceholders(documentData, exportParams, fileNameTemplate);
        String evaluatedFileName = evaluateVelocity(documentData, fileNameTemplate);
        return replaceIllegalFileNameSymbols(evaluatedFileName).trim();
    }

    @VisibleForTesting
    @NotNull String evaluateVelocity(@NotNull DocumentData<? extends IUniqueObject> documentData, @NotNull String fileNameTemplate) {
        String evaluatedName = velocityEvaluator.evaluateVelocityExpressions(documentData, fileNameTemplate);
        return String.format("%s.docx", evaluatedName);
    }

    @VisibleForTesting
    @NotNull String replaceIllegalFileNameSymbols(@NotNull String fileName) {
        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    @VisibleForTesting
    @NotNull String getFileNameTemplate(@NotNull FileNameTemplateModel fileNameTemplateModel) {
        return fileNameTemplateModel.getDocumentNameTemplate();
    }

    private FileNameTemplateModel getFileNameTemplateModel(String scope) {
        try {
            FileNameTemplateSettings fileNameTemplateSettings = (FileNameTemplateSettings) NamedSettingsRegistry.INSTANCE.getByFeatureName(FileNameTemplateSettings.FEATURE_NAME);
            return fileNameTemplateSettings.read(scope, SettingId.fromName(NamedSettings.DEFAULT_NAME), null);
        } catch (IllegalStateException e) {
            return ExceptionHandler.handleTransactionIllegalStateException(e, new FileNameTemplateSettings().defaultValues());
        }
    }
}
