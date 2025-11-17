package ch.sbb.polarion.extension.docx_exporter.util.exporter;

import com.polarion.alm.shared.api.utils.html.HtmlContentBuilder;
import com.polarion.alm.shared.api.utils.html.HtmlFragmentBuilder;
import com.polarion.alm.shared.api.utils.html.RichTextRenderTarget;
import com.polarion.alm.shared.api.utils.html.impl.HtmlBuilder;
import com.polarion.alm.shared.rt.RichTextRenderingContext;
import com.polarion.alm.shared.rt.document.PartIdGeneratorImpl;
import com.polarion.alm.shared.rt.parts.impl.readonly.PageBreakPart;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;

/**
 * Extended version of {@link com.polarion.alm.shared.dle.document.DocumentRenderer}.
 */
public class CustomPageBreakPart extends PageBreakPart {

    public CustomPageBreakPart(PageBreakPart object) {
        super(object.getElement(), new PartIdGeneratorImpl());
    }

    /**
     * Instead of &lt;pd4ml:page.break&gt; we put specific tag.
     */
    @Override
    @SneakyThrows
    @SuppressWarnings("java:S3011")
    public void render(@NotNull HtmlBuilder builder, @NotNull RichTextRenderingContext context, int index) {
        RichTextRenderTarget target = context.getRenderTarget();
        if (target.isPdf()) {
            boolean landscape = Boolean.parseBoolean(this.element.getAttribute().byName("data-is-landscape"));

            Method appendFragmentMethod = HtmlBuilder.class.getDeclaredMethod("appendFragment");
            appendFragmentMethod.setAccessible(true);
            HtmlFragmentBuilder fragment = (HtmlFragmentBuilder) appendFragmentMethod.invoke(builder);

            Method htmlMethod = HtmlContentBuilder.class.getDeclaredMethod("html", String.class);
            htmlMethod.setAccessible(true);

            String pageBreak = "<p>\\newpage</p>" + (landscape ? "<p>\\pageLandscape</p>" : "<p>\\pagePortrait</p>");
            htmlMethod.invoke(fragment, pageBreak);
            fragment.finished();
        } else {
            super.render(builder, context, index);
        }
    }
}
