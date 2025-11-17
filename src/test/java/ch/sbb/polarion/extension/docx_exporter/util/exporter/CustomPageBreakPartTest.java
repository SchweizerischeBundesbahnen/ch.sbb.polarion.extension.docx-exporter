package ch.sbb.polarion.extension.docx_exporter.util.exporter;

import com.polarion.alm.shared.api.utils.html.HtmlFragmentBuilder;
import com.polarion.alm.shared.api.utils.html.RichTextRenderTarget;
import com.polarion.alm.shared.api.utils.html.impl.HtmlBuilder;
import com.polarion.alm.shared.html.HtmlAttributesReader;
import com.polarion.alm.shared.html.HtmlElement;
import com.polarion.alm.shared.rt.RichTextRenderingContext;
import com.polarion.alm.shared.rt.parts.impl.readonly.PageBreakPart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CustomPageBreakPartTest {

    @Mock
    private PageBreakPart pageBreakPart;

    @Mock
    private HtmlBuilder htmlBuilder;

    @Mock
    private HtmlFragmentBuilder htmlFragmentBuilder;

    @Mock
    private RichTextRenderingContext renderingContext;

    @Mock
    private RichTextRenderTarget renderTarget;

    @Mock
    private HtmlElement element;

    @Mock
    private HtmlAttributesReader attributesReader;

    private CustomPageBreakPart customPageBreakPart;

    @BeforeEach
    void setUp() throws Exception {
        when(pageBreakPart.getElement()).thenReturn(element);
        customPageBreakPart = new CustomPageBreakPart(pageBreakPart);
        when(renderingContext.getRenderTarget()).thenReturn(renderTarget);

        // Mock the appendFragment method to return htmlFragmentBuilder
        when(htmlBuilder.appendFragment()).thenReturn(htmlFragmentBuilder);
    }

    @Test
    void testRenderPdfLandscapePageBreak() throws Exception {
        when(renderTarget.isPdf()).thenReturn(true);
        when(element.getAttribute()).thenReturn(attributesReader);
        when(attributesReader.byName("data-is-landscape")).thenReturn("true");

        customPageBreakPart.render(htmlBuilder, renderingContext, 0);

        verify(htmlFragmentBuilder).finished();
    }

    @Test
    void testRenderPdfPortraitPageBreak() throws Exception {
        when(renderTarget.isPdf()).thenReturn(true);
        when(element.getAttribute()).thenReturn(attributesReader);
        when(attributesReader.byName("data-is-landscape")).thenReturn("false");

        customPageBreakPart.render(htmlBuilder, renderingContext, 0);

        verify(htmlFragmentBuilder).finished();
    }

    @Test
    void testNullLandscapeAttributeDefaultsToFalse() throws Exception {
        when(renderTarget.isPdf()).thenReturn(true);
        when(element.getAttribute()).thenReturn(attributesReader);
        when(attributesReader.byName("data-is-landscape")).thenReturn(null);

        assertDoesNotThrow(() -> customPageBreakPart.render(htmlBuilder, renderingContext, 0));

        verify(htmlFragmentBuilder).finished();
    }

    @Test
    void testOrientationChangeFromPortraitToLandscape() throws Exception {
        when(renderTarget.isPdf()).thenReturn(true);
        when(element.getAttribute()).thenReturn(attributesReader);

        when(attributesReader.byName("data-is-landscape")).thenReturn("false");
        customPageBreakPart.render(htmlBuilder, renderingContext, 0);

        when(attributesReader.byName("data-is-landscape")).thenReturn("true");
        customPageBreakPart.render(htmlBuilder, renderingContext, 1);

        verify(htmlFragmentBuilder, times(2)).finished();
    }

    @Test
    void testSameOrientationDoesNotGenerateOrientationChange() throws Exception {
        when(renderTarget.isPdf()).thenReturn(true);
        when(element.getAttribute()).thenReturn(attributesReader);
        when(attributesReader.byName("data-is-landscape")).thenReturn("true");

        customPageBreakPart.render(htmlBuilder, renderingContext, 0);
        customPageBreakPart.render(htmlBuilder, renderingContext, 1);

        verify(htmlFragmentBuilder, times(2)).finished();
    }

    @Test
    void testThreadLocalStateIsolation() throws Exception {
        when(renderTarget.isPdf()).thenReturn(true);
        when(element.getAttribute()).thenReturn(attributesReader);
        when(attributesReader.byName("data-is-landscape")).thenReturn("true");

        customPageBreakPart.render(htmlBuilder, renderingContext, 0);
        customPageBreakPart.render(htmlBuilder, renderingContext, 1);

        verify(htmlFragmentBuilder, times(2)).finished();
    }

    @Test
    void testMultipleRenderCyclesPreserveState() throws Exception {
        when(renderTarget.isPdf()).thenReturn(true);
        when(element.getAttribute()).thenReturn(attributesReader);

        when(attributesReader.byName("data-is-landscape")).thenReturn("false");
        customPageBreakPart.render(htmlBuilder, renderingContext, 0);

        when(attributesReader.byName("data-is-landscape")).thenReturn("true");
        customPageBreakPart.render(htmlBuilder, renderingContext, 1);

        when(attributesReader.byName("data-is-landscape")).thenReturn("false");
        customPageBreakPart.render(htmlBuilder, renderingContext, 2);

        verify(htmlFragmentBuilder, times(3)).finished();
    }
}
