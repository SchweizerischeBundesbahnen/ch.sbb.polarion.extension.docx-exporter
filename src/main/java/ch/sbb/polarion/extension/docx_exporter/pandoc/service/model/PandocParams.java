package ch.sbb.polarion.extension.docx_exporter.pandoc.service.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polarion.core.util.StringUtils;
import lombok.Builder;
import lombok.Data;

import java.util.Map;
import java.util.stream.Collectors;

@Data
@Builder
public class PandocParams {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private String orientation;
    private String paperSize;

    public String toUrlParams() {
        String params = Map.of(
                        "orientation", StringUtils.getEmptyIfNull(orientation),
                        "paper_size", StringUtils.getEmptyIfNull(paperSize)
                ).entrySet().stream()
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
