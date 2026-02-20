package ch.sbb.polarion.extension.docx_exporter.util.placeholder;

import java.util.Arrays;

public enum Placeholder {
    PROJECT_NAME,
    DOCUMENT_ID,
    DOCUMENT_TITLE,
    DOCUMENT_REVISION,
    DOCUMENT_FILTER,
    REVISION,
    REVISION_AND_BASELINE_NAME,
    BASELINE_NAME,
    PRODUCT_NAME,
    PRODUCT_VERSION,
    TIMESTAMP;

    public static boolean contains(String value) {
        return Arrays.stream(Placeholder.values()).anyMatch(placeholder -> placeholder.name().equals(value));
    }

}
