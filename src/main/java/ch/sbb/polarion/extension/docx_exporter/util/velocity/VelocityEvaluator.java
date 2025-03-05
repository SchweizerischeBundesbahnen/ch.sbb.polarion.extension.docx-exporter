package ch.sbb.polarion.extension.docx_exporter.util.velocity;

import ch.sbb.polarion.extension.generic.util.ObjectUtils;
import ch.sbb.polarion.extension.docx_exporter.rest.model.documents.DocumentData;
import com.polarion.alm.projects.model.IUniqueObject;
import com.polarion.alm.server.api.model.rp.widget.RichPageScriptRenderer;
import com.polarion.alm.server.api.model.rp.widget.impl.RichPageRenderingContextImpl;
import com.polarion.alm.shared.api.transaction.TransactionalExecutor;
import com.polarion.alm.shared.api.transaction.internal.InternalReadOnlyTransaction;
import com.polarion.alm.tracker.model.IModule;
import org.apache.velocity.VelocityContext;
import org.jetbrains.annotations.NotNull;

public class VelocityEvaluator {

    public @NotNull String evaluateVelocityExpressions(@NotNull DocumentData<? extends IUniqueObject> documentData, @NotNull String template) {
        return ObjectUtils.requireNotNull(TransactionalExecutor.executeSafelyInReadOnlyTransaction(transaction -> {
            RichPageRenderingContextImpl richPageRenderingContext = new RichPageRenderingContextImpl((InternalReadOnlyTransaction) transaction);
            RichPageScriptRenderer richPageScriptRenderer = new RichPageScriptRenderer(richPageRenderingContext, template, documentData.getId().getDocumentId());
            VelocityContext velocityContext = richPageScriptRenderer.velocityContext();
            updateVelocityContext(velocityContext, documentData);
            return richPageScriptRenderer.renderHtml();
        }));
    }

    private void updateVelocityContext(@NotNull VelocityContext velocityContext, @NotNull DocumentData<? extends IUniqueObject> documentData) {
        if (documentData.getDocumentObject() instanceof IModule) {
            velocityContext.put("document", documentData.getDocumentObject());
        }
        velocityContext.put("projectName", documentData.getId().getDocumentProject() != null ? documentData.getId().getDocumentProject().getName() : "");
    }
}
