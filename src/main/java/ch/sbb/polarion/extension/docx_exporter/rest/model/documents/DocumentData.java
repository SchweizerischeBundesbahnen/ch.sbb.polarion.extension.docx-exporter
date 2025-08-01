package ch.sbb.polarion.extension.docx_exporter.rest.model.documents;

import ch.sbb.polarion.extension.docx_exporter.rest.model.documents.id.DocumentId;
import com.polarion.alm.projects.model.IUniqueObject;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Getter
public class DocumentData<T extends IUniqueObject> {
    @Setter(AccessLevel.NONE)
    private final @NotNull T documentObject;

    @Setter(AccessLevel.NONE)
    private final @NotNull DocumentId id;
    @Setter(AccessLevel.NONE)
    private final @NotNull String title;

    private final @Nullable String content;

    private final @Nullable DocumentBaseline baseline;
    private final @Nullable String revision;
    private final @NotNull String lastRevision;
    private final @NotNull String revisionPlaceholder;

    public static <T extends IUniqueObject> DocumentDataBuilder<T> creator(@NotNull T documentObject) {
        return DocumentData.<T>builder()
                .documentObject(documentObject);
    }

    // making javadoc maven plugin happy
    @SuppressWarnings("unused")
    public static class DocumentDataBuilder<T extends IUniqueObject> {}
}
