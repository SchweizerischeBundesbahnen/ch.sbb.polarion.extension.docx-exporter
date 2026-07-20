package ch.sbb.polarion.extension.docx_exporter.util.velocity;

import ch.sbb.polarion.extension.generic.util.ObjectUtils;
import ch.sbb.polarion.extension.docx_exporter.rest.model.documents.DocumentData;
import com.polarion.alm.projects.model.IUniqueObject;
import com.polarion.alm.server.api.model.rp.widget.RichPageScriptRenderer;
import com.polarion.alm.server.api.model.rp.widget.impl.RichPageRenderingContextImpl;
import com.polarion.alm.shared.api.transaction.TransactionalExecutor;
import com.polarion.alm.shared.api.transaction.internal.InternalReadOnlyTransaction;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.wiki.IWikiService;
import com.polarion.platform.core.PlatformContext;
import org.apache.velocity.VelocityContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

public class VelocityEvaluator {

    public @NotNull String evaluateVelocityExpressions(@NotNull DocumentData<? extends IUniqueObject> documentData, @NotNull String template) {
        return ObjectUtils.requireNotNull(TransactionalExecutor.executeSafelyInReadOnlyTransaction(transaction -> {
            RichPageRenderingContextImpl richPageRenderingContext = new RichPageRenderingContextImpl((InternalReadOnlyTransaction) transaction);
            RichPageScriptRenderer richPageScriptRenderer = new RichPageScriptRenderer(richPageRenderingContext, template, documentData.getId().getDocumentId());
            VelocityContext velocityContext = richPageScriptRenderer.velocityContext();
            addWikiRenderingContext(velocityContext);
            updateVelocityContext(velocityContext, documentData);
            return richPageScriptRenderer.renderHtml();
        }));
    }

    /**
     * Rich page velocity context, which is used here to evaluate expressions, doesn't contain wiki specific tools
     * such as $calendarTool or $wikiService, they are contributed to the wiki rendering context only. Without them
     * expressions which work fine in wiki content are left unevaluated in exported document, so we add them here.
     * Values already present in the context take precedence and are not overwritten.
     */
    @VisibleForTesting
    void addWikiRenderingContext(@NotNull VelocityContext velocityContext) {
        IWikiService wikiService = PlatformContext.getPlatform().lookupService(IWikiService.class);
        if (wikiService == null) {
            return;
        }
        wikiService.getWikiRenderingContextMap().forEach((key, value) -> {
            if (velocityContext.get(key) == null) {
                velocityContext.put(key, value);
            }
        });
    }

    private void updateVelocityContext(@NotNull VelocityContext velocityContext, @NotNull DocumentData<? extends IUniqueObject> documentData) {
        if (documentData.getDocumentObject() instanceof IModule) {
            velocityContext.put("document", documentData.getDocumentObject());
        }
        velocityContext.put("projectName", documentData.getId().getDocumentProject() != null ? documentData.getId().getDocumentProject().getName() : "");
    }
}
