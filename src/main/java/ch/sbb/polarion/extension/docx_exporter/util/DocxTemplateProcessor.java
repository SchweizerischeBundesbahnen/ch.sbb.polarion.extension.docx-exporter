package ch.sbb.polarion.extension.docx_exporter.util;

import ch.sbb.polarion.extension.docx_exporter.rest.model.conversion.ExportParams;
import ch.sbb.polarion.extension.docx_exporter.rest.model.documents.DocumentData;
import ch.sbb.polarion.extension.docx_exporter.util.placeholder.PlaceholderProcessor;
import ch.sbb.polarion.extension.docx_exporter.util.velocity.VelocityEvaluator;
import ch.sbb.polarion.extension.generic.util.BundleJarsPrioritizingRunnable;
import ch.sbb.polarion.extension.generic.util.ScopeUtils;
import com.polarion.alm.projects.model.IUniqueObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.Map;
import java.util.function.UnaryOperator;

import static ch.sbb.polarion.extension.docx_exporter.util.OpenXMLTextEntriesProcessor.*;

public class DocxTemplateProcessor {

    private final VelocityEvaluator velocityEvaluator;
    private final PlaceholderProcessor placeholderProcessor;

    public DocxTemplateProcessor() {
        this.velocityEvaluator = new VelocityEvaluator();
        this.placeholderProcessor = new PlaceholderProcessor();
    }

    @VisibleForTesting
    public DocxTemplateProcessor(VelocityEvaluator velocityEvaluator, PlaceholderProcessor placeholderProcessor) {
        this.velocityEvaluator = velocityEvaluator;
        this.placeholderProcessor = placeholderProcessor;
    }

    @NotNull
    public String processUsing(@NotNull String documentName, @NotNull String content) {
        return ScopeUtils.getFileContent("webapp/docx-exporter/html/docxTemplate.html")
                .replace("{DOC_NAME}", documentName)
                .replace("{DOC_CONTENT}", content);
    }

    public byte[] processDocxTemplate(byte[] template, DocumentData<? extends IUniqueObject> documentData, @NotNull ExportParams exportParams) {
        Map<String, Object> params = Map.of(
                PARAM_PROCESS_TEMPLATE, template,
                PARAM_PROCESS_FUNCTION, (UnaryOperator<String>) text -> processPlaceholdersAndVelocity(documentData, exportParams, text)
        );
        return (byte[]) BundleJarsPrioritizingRunnable.execute(OpenXMLTextEntriesProcessor.class, params, true).get(PARAM_PROCESS_RESULT);
    }

    private String processPlaceholdersAndVelocity(DocumentData<? extends IUniqueObject> documentData, ExportParams exportParams, String text) {
        String afterPlaceholdersReplacement = placeholderProcessor.replacePlaceholders(documentData, exportParams, text);
        return velocityEvaluator.evaluateVelocityExpressions(documentData, afterPlaceholdersReplacement);
    }

}
