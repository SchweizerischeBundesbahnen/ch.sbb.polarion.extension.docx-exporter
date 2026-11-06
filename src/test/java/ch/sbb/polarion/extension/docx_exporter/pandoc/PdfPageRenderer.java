package ch.sbb.polarion.extension.docx_exporter.pandoc;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Renders each page of a PDF into {@code <prefix><page>.png}, as {@code MediaUtils.pdfPageToImage} does.
 * {@link PageRendererContainer} runs it inside its container, with only PDFBox and this class on the class path:
 * keep it free of other classes of the project, nested classes included.
 */
public final class PdfPageRenderer {

    private PdfPageRenderer() {
    }

    /**
     * @param args the PDF, the prefix of the PNGs, the DPI
     */
    public static void main(String[] args) throws IOException {
        float dpi = Float.parseFloat(args[2]);
        try (PDDocument document = Loader.loadPDF(new File(args[0]))) {
            for (int page = 0; page < document.getNumberOfPages(); page++) {
                BufferedImage image = new PDFRenderer(document).renderImageWithDPI(page, dpi);
                ImageIO.write(image, "png", new File(args[1] + page + ".png"));
            }
        }
    }
}
