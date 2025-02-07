package ch.sbb.polarion.extension.docx_exporter.pandoc.service.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class PandocInfo {
    private @Nullable String python;
    private @Nullable String timestamp;
    private @Nullable String pandoc;
    private @Nullable String pandocService;
}
