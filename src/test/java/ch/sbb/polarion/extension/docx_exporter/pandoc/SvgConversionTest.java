package ch.sbb.polarion.extension.docx_exporter.pandoc;

import ch.sbb.polarion.extension.docx_exporter.pandoc.service.model.PandocParams;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that pandoc-service turns the SVG images embedded in the HTML into pictures Word draws.
 * <p>
 * docx-exporter inlines every image as {@code <img src="data:image/svg+xml;base64,...">}; Word cannot draw draw.io's
 * {@code <foreignObject>} SVGs. With either SVG renderer of pandoc-service the picture is a PNG in {@code word/media/}
 * referenced by {@code <a:blip>}. Chromium leaves it at that. weasysvg adds the SVG flattened to paths, clips and
 * gradients as the {@code svgBlip} extension of the picture, as Word saves an SVG: no {@code foreignObject}, filter
 * or mask is left in it.
 */
@SkipTestWhenParamNotSet
class SvgConversionTest extends BasePandocTest {

    @Test
    void inlineSvgImageIsRasterizedToPngInDocx() throws Exception {
        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" width="120" height="60" viewBox="0 0 120 60">
                  <rect x="0" y="0" width="120" height="60" fill="#e0f0ff" stroke="#0066cc" stroke-width="2"/>
                  <text x="60" y="35" font-family="sans-serif" font-size="14" text-anchor="middle" fill="#003366">Hello SVG</text>
                </svg>
                """;

        byte[] docBytes = exportToDOCX(htmlWithSvgImage(svg), readTemplate("reference_template"), PandocParams.builder().build());
        assertNotNull(docBytes);
        writeReportDocx("inlineSvgImageIsRasterizedToPngInDocx_generated", docBytes);

        assertSvgRasterizedToPng(docBytes);
    }

    @Test
    void drawioSvgWithForeignObjectIsRasterizedToPngInDocx() throws Exception {
        // draw.io / mxgraph SVG using <switch> + requiredFeatures="...#Extensibility" + <foreignObject>.
        // Word lands on the <text> fallback branch instead of rendering this, which is exactly the bug the
        // pandoc-service rasterization fixes; Chromium and weasysvg both draw the foreignObject.
        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="160" height="80" viewBox="-0.5 -0.5 160 80">
                  <g>
                    <switch>
                      <foreignObject pointer-events="none" width="100%" height="100%" requiredFeatures="http://www.w3.org/TR/SVG11/feature#Extensibility" style="overflow: visible; text-align: left;">
                        <div xmlns="http://www.w3.org/1999/xhtml" style="display: flex; align-items: center; justify-content: center; width: 158px; height: 78px; font-family: sans-serif; font-size: 14px; color: #1a1a1a; border: 2px solid #6c8ebf; background: #dae8fc;">draw.io diagram</div>
                      </foreignObject>
                      <text x="80" y="44" fill="#1a1a1a" font-family="sans-serif" font-size="14" text-anchor="middle">Viewer does not support full SVG 1.1</text>
                    </switch>
                  </g>
                </svg>
                """;

        byte[] docBytes = exportToDOCX(htmlWithSvgImage(svg), readTemplate("reference_template"), PandocParams.builder().build());
        assertNotNull(docBytes);
        writeReportDocx("drawioSvgWithForeignObjectIsRasterizedToPngInDocx_generated", docBytes);

        assertSvgRasterizedToPng(docBytes);
    }

    private static String htmlWithSvgImage(String svg) {
        String dataUrl = "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
        return "<html><head><title>SVG Test</title></head><body>"
                + "<p>Diagram below:</p>"
                + "<img src=\"" + dataUrl + "\" />"
                + "<p>After diagram.</p>"
                + "</body></html>";
    }

    private void assertSvgRasterizedToPng(byte[] docBytes) throws IOException {
        Map<String, byte[]> entries = unzip(docBytes);

        List<String> mediaFiles = entries.keySet().stream()
                .filter(name -> name.startsWith("word/media/"))
                .toList();
        List<String> svgFiles = mediaFiles.stream().filter(name -> name.toLowerCase().endsWith(".svg")).toList();

        assertTrue(mediaFiles.stream().anyMatch(name -> name.toLowerCase().endsWith(".png")), "Expected a rasterized PNG in word/media/, found: " + mediaFiles);

        byte[] documentXmlBytes = entries.get("word/document.xml");
        assertNotNull(documentXmlBytes, "word/document.xml should be present in the DOCX");
        String documentXml = new String(documentXmlBytes, StandardCharsets.UTF_8);
        assertTrue(documentXml.contains("<a:blip"), "document.xml should reference the embedded image via <a:blip>");

        // An SVG comes only as the svgBlip of its PNG, and holds only what Word draws
        assertEquals(!svgFiles.isEmpty(), documentXml.contains("svgBlip"), "An SVG in word/media/ goes with an svgBlip extension, found: " + mediaFiles);
        for (String svgFile : svgFiles) {
            String svg = new String(entries.get(svgFile), StandardCharsets.UTF_8);
            assertFalse(svg.contains("foreignObject"), svgFile + " should not contain a foreignObject");
            assertFalse(svg.contains("filter="), svgFile + " should not contain a filter");
            assertFalse(svg.contains("<mask"), svgFile + " should not contain a mask");
        }
    }

    private static Map<String, byte[]> unzip(byte[] docx) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(docx))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries.put(entry.getName(), zis.readAllBytes());
            }
        }
        return entries;
    }
}
