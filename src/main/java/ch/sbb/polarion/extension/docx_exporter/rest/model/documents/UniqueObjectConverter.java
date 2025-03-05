package ch.sbb.polarion.extension.docx_exporter.rest.model.documents;

import ch.sbb.polarion.extension.docx_exporter.rest.model.conversion.ExportParams;
import ch.sbb.polarion.extension.docx_exporter.rest.model.documents.adapters.IUniqueObjectAdapter;
import ch.sbb.polarion.extension.docx_exporter.rest.model.documents.adapters.LiveDocAdapter;
import ch.sbb.polarion.extension.docx_exporter.service.PolarionBaselineExecutor;
import com.polarion.alm.projects.model.IUniqueObject;
import com.polarion.alm.shared.api.model.ModelObject;
import com.polarion.alm.shared.api.transaction.ReadOnlyTransaction;
import com.polarion.alm.shared.api.transaction.TransactionalExecutor;
import com.polarion.alm.tracker.model.IModule;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class UniqueObjectConverter {

    @Getter
    private final @NotNull IUniqueObjectAdapter uniqueObjectAdapter;
    private boolean withContent;
    private @Nullable ExportParams exportParams;

    public UniqueObjectConverter(@NotNull IUniqueObject uniqueObject) {
        if (uniqueObject instanceof IModule module) {
            uniqueObjectAdapter = new LiveDocAdapter(module);
        } else {
            throw new IllegalArgumentException("Unsupported unique object type: " + uniqueObject.getClass());
        }
    }

    public UniqueObjectConverter(@NotNull ModelObject modelObject) {
        this((IUniqueObject) modelObject.getOldApi());
    }

    public UniqueObjectConverter withExportParams(ExportParams exportParams) {
        this.exportParams = exportParams;
        return this;
    }

    public UniqueObjectConverter withContent(boolean withContent) {
        this.withContent = withContent;
        return this;
    }

    public @NotNull <T extends IUniqueObject> DocumentData<T> toDocumentData() {
        return Objects.requireNonNull(TransactionalExecutor.executeSafelyInReadOnlyTransaction(this::toDocumentData));
    }

    public @NotNull <T extends IUniqueObject> DocumentData<T> toDocumentData(@NotNull ReadOnlyTransaction transaction) {
        String baselineRevision = getBaselineRevision();
        return PolarionBaselineExecutor.executeInBaseline(baselineRevision, transaction, () -> DocumentData.<T>builder()
                .documentObject(uniqueObjectAdapter.getUniqueObject())
                .id(uniqueObjectAdapter.getDocumentId())
                .title(uniqueObjectAdapter.getTitle())
                .revision(uniqueObjectAdapter.getRevision())
                .lastRevision(uniqueObjectAdapter.getLastRevision())
                .revisionPlaceholder(uniqueObjectAdapter.getRevisionPlaceholder())
                .baseline(uniqueObjectAdapter.getDocumentBaseline(transaction))
                .content(withContent && exportParams != null ? uniqueObjectAdapter.getContent(exportParams, transaction) : null)
                .build());
    }

    private @Nullable String getBaselineRevision() {
        return exportParams != null ? exportParams.getBaselineRevision() : null;
    }

}
