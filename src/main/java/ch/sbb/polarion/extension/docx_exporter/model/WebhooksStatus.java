package ch.sbb.polarion.extension.docx_exporter.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhooksStatus {
    private Boolean enabled;
}
