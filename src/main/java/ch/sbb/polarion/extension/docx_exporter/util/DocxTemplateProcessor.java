package ch.sbb.polarion.extension.docx_exporter.util;

import ch.sbb.polarion.extension.generic.util.ScopeUtils;
import org.jetbrains.annotations.NotNull;

public class DocxTemplateProcessor {
    @NotNull
    public String processUsing(@NotNull String documentName, @NotNull String content) {
        return ScopeUtils.getFileContent("webapp/docx-exporter/html/docxTemplate.html")
                .replace("{DOC_NAME}", documentName)
                .replace("{DOC_CONTENT}", content);
    }

    public String buildSizeCss() {
        return " @page {size: A4 %portrait;}";
    }
}
