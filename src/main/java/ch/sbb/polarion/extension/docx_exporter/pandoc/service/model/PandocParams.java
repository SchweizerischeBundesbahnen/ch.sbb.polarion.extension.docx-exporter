package ch.sbb.polarion.extension.docx_exporter.pandoc.service.model;

import ch.sbb.polarion.extension.docx_exporter.rest.model.conversion.ImageDensity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polarion.core.util.StringUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PandocParams {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private String orientation;
    private String paperSize;
    private ImageDensity imageDensity;

    public String toUrlParams() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("orientation", StringUtils.getEmptyIfNull(orientation));
        values.put("paper_size", StringUtils.getEmptyIfNull(paperSize));
        // pandoc-service expects the device scale factor (same contract as weasyprint-service)
        values.put("scale_factor", imageDensity == null ? "" : String.valueOf(imageDensity.getScale()));
        String params = values.entrySet().stream()
                .filter(entry -> !StringUtils.isEmpty(entry.getValue()))
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
        return params.isEmpty() ? "" : "?" + params;
    }

    public static PandocParams fromJson(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, PandocParams.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse PandocParams from JSON: " + json, e);
        }
    }
}
