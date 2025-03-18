package ch.sbb.polarion.extension.docx_exporter.rest.model.documents;

import ch.sbb.polarion.extension.docx_exporter.rest.model.conversion.ExportParams;
import ch.sbb.polarion.extension.docx_exporter.service.DocxExporterPolarionService;
import com.polarion.alm.projects.model.IProject;
import com.polarion.alm.shared.api.model.ModelObject;
import com.polarion.alm.shared.api.model.document.Document;
import com.polarion.alm.shared.api.model.document.DocumentReference;
import com.polarion.alm.shared.api.transaction.ReadOnlyTransaction;
import com.polarion.core.util.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class ModelObjectProvider {

    private static final String LOCATION_PATH_IS_REQUIRED_FOR_EXPORT = "Location path is required for export";
    private static final String PROJECT_ID_IS_REQUIRED_FOR_EXPORT = "Project id is required for export";

    private final @NotNull ExportParams exportParams;
    private final @NotNull DocxExporterPolarionService docxExporterPolarionService;

    public ModelObjectProvider(@NotNull ExportParams exportParams) {
        this.exportParams = exportParams;
        this.docxExporterPolarionService = new DocxExporterPolarionService();
    }

    public ModelObjectProvider(@NotNull ExportParams exportParams, @NotNull DocxExporterPolarionService docxExporterPolarionService) {
        this.exportParams = exportParams;
        this.docxExporterPolarionService = docxExporterPolarionService;
    }

    public ModelObject getModelObject(ReadOnlyTransaction transaction) {
        return getDocument(transaction);
    }

    public Document getDocument(ReadOnlyTransaction transaction) {
        String projectId = getProjectId().orElseThrow(() -> new IllegalArgumentException(PROJECT_ID_IS_REQUIRED_FOR_EXPORT));
        String locationPath = getLocationPath().orElseThrow(() -> new IllegalArgumentException(LOCATION_PATH_IS_REQUIRED_FOR_EXPORT));

        DocumentReference documentReference = DocumentReference.fromPath(createPath(projectId, locationPath));
        if (exportParams.getRevision() != null) {
            documentReference = documentReference.getWithRevision(exportParams.getRevision());
        }

        return documentReference.getOriginal(transaction);
    }


    private Optional<String> getProjectId() {
        IProject project = null;
        if (!StringUtils.isEmpty(exportParams.getProjectId())) {
            project = docxExporterPolarionService.getProject(exportParams.getProjectId());
        }
        String projectId = project != null ? project.getId() : null;
        return Optional.ofNullable(projectId);
    }

    private Optional<String> getLocationPath() {
        return Optional.ofNullable(exportParams.getLocationPath());
    }

    public static @NotNull String createPath(@NotNull String projectId, @NotNull String locationPath) {
        return String.format("%s/%s", projectId, locationPath);
    }

}
