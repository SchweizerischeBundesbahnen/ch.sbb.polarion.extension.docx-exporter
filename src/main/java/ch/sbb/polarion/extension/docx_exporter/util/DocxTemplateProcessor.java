package ch.sbb.polarion.extension.docx_exporter.util;

import ch.sbb.polarion.extension.docx_exporter.rest.model.conversion.ExportParams;
import ch.sbb.polarion.extension.docx_exporter.rest.model.documents.DocumentData;
import ch.sbb.polarion.extension.docx_exporter.util.placeholder.PlaceholderProcessor;
import ch.sbb.polarion.extension.docx_exporter.util.velocity.VelocityEvaluator;
import ch.sbb.polarion.extension.generic.util.BundleJarsPrioritizingRunnable;
import ch.sbb.polarion.extension.generic.util.ScopeUtils;
import com.polarion.alm.projects.model.IUniqueObject;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.Map;
import java.util.function.Function;

public class DocxTemplateProcessor {

    public static final String PARAM_PROCESS_TEMPLATE = "template";
    public static final String PARAM_PROCESS_FUNCTION = "processFunction";
    public static final String PARAM_PROCESS_RESULT = "result";

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

    public String buildSizeCss() {
        return " @page {size: A4 %portrait;}";
    }

    @SneakyThrows
    public byte[] processDocxTemplate(byte[] template, DocumentData<? extends IUniqueObject> documentData, @NotNull ExportParams exportParams) {
        Map<String, Object> params = Map.of(
                PARAM_PROCESS_TEMPLATE, template,
                PARAM_PROCESS_FUNCTION, (Function<String, String>) text ->
                        velocityEvaluator.evaluateVelocityExpressions(documentData, placeholderProcessor.replacePlaceholders(documentData, exportParams, text))
        );
        return (byte[]) BundleJarsPrioritizingRunnable.execute(OpenXMLTextEntriesProcessor.class, params).getOrDefault(PARAM_PROCESS_RESULT, template);
    }

}
