package ch.sbb.polarion.extension.docx_exporter.rest.model.documents.adapters;

import ch.sbb.polarion.extension.docx_exporter.rest.model.conversion.CommentsRenderType;
import ch.sbb.polarion.extension.docx_exporter.rest.model.conversion.ExportParams;
import ch.sbb.polarion.extension.docx_exporter.rest.model.documents.id.DocumentId;
import ch.sbb.polarion.extension.docx_exporter.rest.model.documents.id.DocumentProject;
import ch.sbb.polarion.extension.docx_exporter.rest.model.documents.id.LiveDocId;
import ch.sbb.polarion.extension.docx_exporter.util.LiveDocCommentsProcessor;
import ch.sbb.polarion.extension.docx_exporter.util.WorkItemCommentsProcessor;
import ch.sbb.polarion.extension.docx_exporter.util.exporter.ModifiedDocumentRenderer;
import ch.sbb.polarion.extension.generic.service.PolarionBaselineExecutor;
import com.polarion.alm.projects.model.IUniqueObject;
import com.polarion.alm.server.api.model.document.ProxyDocument;
import com.polarion.alm.shared.api.transaction.ReadOnlyTransaction;
import com.polarion.alm.shared.api.transaction.internal.InternalReadOnlyTransaction;
import com.polarion.alm.shared.api.utils.html.RichTextRenderTarget;
import com.polarion.alm.shared.dle.document.DocumentRendererParameters;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.core.util.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class LiveDocAdapter extends CommonUniqueObjectAdapter {
    public static final String DOC_REVISION_CUSTOM_FIELD = "docRevision";

    private final @NotNull IModule module;

    public LiveDocAdapter(@NotNull IModule module) {
        this.module = module;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends IUniqueObject> @NotNull T getUniqueObject() {
        return (T) module;
    }

    @Override
    public @NotNull DocumentId getDocumentId() {
        return new LiveDocId(getDocumentProject(), module.getModuleFolder(), module.getId());
    }

    private @NotNull DocumentProject getDocumentProject() {
        return new DocumentProject(module.getProject());
    }

    @Override
    public @NotNull String getTitle() {
        return module.getTitleOrName();
    }

    @Override
    public @NotNull String getRevisionPlaceholder() {
        Object docRevision = module.getCustomField(DOC_REVISION_CUSTOM_FIELD);
        return docRevision != null ? docRevision.toString() : super.getRevisionPlaceholder();
    }

    @Override
    public @NotNull String getContent(@NotNull ExportParams exportParams, @NotNull ReadOnlyTransaction transaction) {
        return PolarionBaselineExecutor.executeInBaseline(exportParams.getBaselineRevision(), transaction, () -> {
            ProxyDocument document = new ProxyDocument(module, (InternalReadOnlyTransaction) transaction);

            String internalContent = StringUtils.getEmptyIfNull(exportParams.getInternalContent() != null ? exportParams.getInternalContent() : document.getHomePageContentHtml());
            internalContent = processLiveDocComments(exportParams, document, internalContent);
            if (exportParams.getRenderComments() != null) {
                // Add inline comments into document content
                internalContent = new WorkItemCommentsProcessor().addWorkItemComments(document, internalContent, CommentsRenderType.OPEN.equals(exportParams.getRenderComments()));
            }
            ModifiedDocumentRenderer documentRenderer = getDocumentRenderer(exportParams, (InternalReadOnlyTransaction) transaction, document);
            String rendered = documentRenderer.render(internalContent);
            return processLiveDocComments(exportParams, document, rendered);
        });
    }

    static @NotNull ModifiedDocumentRenderer getDocumentRenderer(@NotNull ExportParams exportParams, @NotNull InternalReadOnlyTransaction transaction, @NotNull ProxyDocument document) {
        Map<String, String> documentParameters = exportParams.getUrlQueryParameters() == null ? Map.of() : exportParams.getUrlQueryParameters();
        DocumentRendererParameters parameters = new DocumentRendererParameters(documentParameters.get(ExportParams.URL_QUERY_PARAM_QUERY), documentParameters.get(ExportParams.URL_QUERY_PARAM_LANGUAGE));
        return new ModifiedDocumentRenderer(transaction, document, RichTextRenderTarget.PDF_EXPORT, parameters);
    }

    private String processLiveDocComments(@NotNull ExportParams exportParams, @NotNull ProxyDocument document, @NotNull String content) {
        return new LiveDocCommentsProcessor().addLiveDocComments(document, content, exportParams.getRenderComments());
    }

}
