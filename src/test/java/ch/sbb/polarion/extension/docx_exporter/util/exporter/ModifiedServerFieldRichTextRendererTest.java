package ch.sbb.polarion.extension.docx_exporter.util.exporter;

import com.polarion.alm.server.rt.parts.Renderer;
import com.polarion.alm.shared.api.model.document.DocumentReference;
import com.polarion.alm.shared.api.model.document.internal.InternalDocument;
import com.polarion.alm.shared.api.model.wi.WorkItemReference;
import com.polarion.alm.shared.api.transaction.ReadOnlyTransaction;
import com.polarion.alm.shared.api.utils.html.HtmlContentBuilder;
import com.polarion.alm.shared.api.utils.html.RichTextRenderTarget;
import com.polarion.alm.shared.rt.RichTextRenderingContext;
import lombok.SneakyThrows;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ModifiedServerFieldRichTextRendererTest {

    @Test
    void testConstructor() {
        ReadOnlyTransaction transaction = mock(ReadOnlyTransaction.class, RETURNS_DEEP_STUBS);

        assertDoesNotThrow(() -> new ModifiedServerFieldRichTextRenderer(transaction),
                "Constructor should not throw exception with mocked transaction");
    }

    @Test
    @SuppressWarnings("unused")
    void testRenderDescription() {
        ModifiedServerFieldRichTextRenderer renderer = spy(new ModifiedServerFieldRichTextRenderer(mock(ReadOnlyTransaction.class, RETURNS_DEEP_STUBS)));
        RichTextRenderingContext context = mock(RichTextRenderingContext.class, RETURNS_DEEP_STUBS);
        // RichTextRenderTarget is a sealed abstract enum in Polarion 2606 and cannot be (deep-)mocked; return a real literal.
        // doReturn(...).when(...) is required here: when(context.getRenderTarget()) would invoke the deep stub, which eagerly
        // tries to mock the RichTextRenderTarget return type and fails.
        doReturn(RichTextRenderTarget.PREVIEW).when(context).getRenderTarget();
        renderer.setRichTextRenderingContext(context);
        WorkItemReference workItemReference = mock(WorkItemReference.class, RETURNS_DEEP_STUBS);
        try (MockedConstruction<Renderer> rendererConstructionMock = mockConstruction(Renderer.class);
             MockedStatic<FieldUtils> mockFieldUtils = mockStatic(FieldUtils.class)) {
            assertDoesNotThrow(() -> renderer.renderDescription(mock(HtmlContentBuilder.class), workItemReference, true));

            // test @SneakyThrows
            mockFieldUtils.when(() -> FieldUtils.writeField(eq(context), eq("renderTarget"), any(), anyBoolean())).thenThrow(new IllegalAccessException("Test"));
            assertThrows(IllegalAccessException.class, () -> renderer.renderDescription(mock(HtmlContentBuilder.class), workItemReference, true));
        }
    }

    /** Polarion renders the icons of the comments which refer to a work item for a preview, and not for a PDF. */
    @Test
    @SneakyThrows
    void rendersTheIconsOfTheCommentsWhichReferToAWorkItem() {
        ReadOnlyTransaction transaction = mock(ReadOnlyTransaction.class, RETURNS_DEEP_STUBS);
        ModifiedServerFieldRichTextRenderer renderer = new ModifiedServerFieldRichTextRenderer(transaction);
        RichTextRenderingContext context = mock(RichTextRenderingContext.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
        FieldUtils.writeField(context, "renderTarget", RichTextRenderTarget.PDF_EXPORT, true);
        renderer.setRichTextRenderingContext(context);

        RichTextRenderTarget[] targetWhileRendering = {null};
        InternalDocument document = mock(InternalDocument.class);
        when(document.isUnresolvable()).thenAnswer(invocation -> {
            targetWhileRendering[0] = context.getRenderTarget();
            return true;
        });
        DocumentReference documentReference = mock(DocumentReference.class);
        doReturn(document).when(documentReference).getOriginal(transaction);

        assertTrue(renderer.renderReferredCommentMarkers(mock(HtmlContentBuilder.class), documentReference, mock(WorkItemReference.class)).isEmpty());

        assertEquals(RichTextRenderTarget.PREVIEW, targetWhileRendering[0], "The icons are rendered as for a preview");
        assertEquals(RichTextRenderTarget.PDF_EXPORT, context.getRenderTarget(), "The render target is restored after");

        try (MockedStatic<FieldUtils> mockFieldUtils = mockStatic(FieldUtils.class)) {
            mockFieldUtils.when(() -> FieldUtils.writeField(eq(context), eq("renderTarget"), any(), anyBoolean())).thenThrow(new IllegalAccessException("Test"));
            assertThrows(IllegalAccessException.class, () -> renderer.renderReferredCommentMarkers(mock(HtmlContentBuilder.class), documentReference, mock(WorkItemReference.class)));
        }
    }

}
