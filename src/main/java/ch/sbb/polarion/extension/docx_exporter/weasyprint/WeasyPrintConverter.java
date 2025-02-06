package ch.sbb.polarion.extension.docx_exporter.weasyprint;

import ch.sbb.polarion.extension.docx_exporter.weasyprint.service.model.WeasyPrintInfo;

public interface WeasyPrintConverter {
    byte[] convertToPdf(String htmlPage, WeasyPrintOptions weasyPrintOptions);

    WeasyPrintInfo getWeasyPrintInfo();
}
