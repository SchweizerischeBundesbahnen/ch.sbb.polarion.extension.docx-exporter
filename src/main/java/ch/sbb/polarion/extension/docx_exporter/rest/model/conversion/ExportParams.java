package ch.sbb.polarion.extension.docx_exporter.rest.model.conversion;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExportParams {
    @Schema(description = "The unique identifier for the project", example = "elibrary")
    private @Nullable String projectId;

    @Schema(description = "Document path for export", example = "Specification/Product Specification")
    private @Nullable String locationPath;

    @Schema(description = "The specific revision of the provided baseline")
    private @Nullable String baselineRevision;

    @Schema(description = "The specific revision of the document")
    private @Nullable String revision;

    @Schema(description = "Localization settings name")
    private String localization;

    @Schema(description = "Webhooks settings name")
    private String webhooks;

    @Schema(description = "Template settings name")
    private String template;

    @Schema(description = "Which comments should be rendered in the exported document")
    private CommentsRenderType renderComments;

    @Schema(description = "Empty chapters should be removed from the document")
    private boolean cutEmptyChapters;

    @Schema(description = "Empty work item attributes should be removed from the document", defaultValue = "true")
    private boolean cutEmptyWIAttributes = true;

    @Schema(description = "Local Polarion URLs should be removed from the document")
    private boolean cutLocalUrls;

    @Schema(description = "Specific higher level chapters")
    private List<String> chapters;

    @Schema(description = "Language in the exported document")
    private String language;

    @Schema(description = "Specific Workitem roles", example = "[\"has parent\", \"depends on\"]")
    private List<String> linkedWorkitemRoles;

    @Schema(description = "Target file name of exported document")
    private String fileName;

    @Schema(description = "CSS-like selector for elements to remove")
    private String removalSelector;

    @Schema(description = "Map of attributes extracted from the URL")
    private Map<String, String> urlQueryParameters;

    @Schema(description = "Internal content")
    private String internalContent;
}
