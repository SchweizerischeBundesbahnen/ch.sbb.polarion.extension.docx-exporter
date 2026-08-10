package ch.sbb.polarion.extension.docx_exporter.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * What the Templates administration page shows about an attached DOCX reference template.
 * {@code modifiedDate} is null when the document carries no core properties, which is legal in OOXML.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TemplateDetails {
    private int styleCount;
    private String modifiedDate;
}
