package ch.sbb.polarion.extension.docx_exporter.pandoc;

import ch.sbb.polarion.extension.docx_exporter.pandoc.service.model.PandocParams;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * {@code <foreignObject>} SVGs. pandoc-service draws the picture with weasysvg as a PNG in {@code word/media/}
 * referenced by {@code <a:blip>}. On request it adds the SVG flattened to paths, clips and gradients as the
 * {@code svgBlip} extension of the picture, as Word saves an SVG: no {@code foreignObject}, filter or mask is left
 * in it.
 */
@SkipTestWhenParamNotSet
class SvgConversionTest extends BasePandocTest {

    private static final String DRAWING_NS = "http://schemas.openxmlformats.org/drawingml/2006/main";
    private static final String SVG_NS = "http://schemas.microsoft.com/office/drawing/2016/SVG/main";
    private static final String RELATIONSHIPS_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String PACKAGE_RELATIONSHIPS_NS = "http://schemas.openxmlformats.org/package/2006/relationships";

    private static final String SIMPLE_SVG = """
            <svg xmlns="http://www.w3.org/2000/svg" width="120" height="60" viewBox="0 0 120 60">
              <rect x="0" y="0" width="120" height="60" fill="#e0f0ff" stroke="#0066cc" stroke-width="2"/>
              <text x="60" y="35" font-family="sans-serif" font-size="14" text-anchor="middle" fill="#003366">Hello SVG</text>
            </svg>
            """;

    @Test
    void inlineSvgImageIsRasterizedToPngInDocx() throws Exception {
        byte[] docBytes = exportToDOCX(htmlWithSvgImage(SIMPLE_SVG), readTemplate("reference_template"), PandocParams.builder().build());
        assertNotNull(docBytes);
        writeReportDocx("inlineSvgImageIsRasterizedToPngInDocx_generated", docBytes);

        assertSvgRasterizedToPng(docBytes);
    }

    @Test
    void drawioSvgWithForeignObjectIsRasterizedToPngInDocx() throws Exception {
        // draw.io / mxgraph SVG using <switch> + requiredFeatures="...#Extensibility" + <foreignObject>.
        // Word lands on the <text> fallback branch instead of rendering this, which is exactly the bug the
        // pandoc-service rasterization fixes; weasysvg draws the foreignObject.
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

    @Test
    void svgIsLeftOutOfDocxByDefault() throws Exception {
        byte[] docBytes = exportToDOCX(htmlWithSvgImage(SIMPLE_SVG), readTemplate("reference_template"), PandocParams.builder().build());
        writeReportDocx("svgIsLeftOutOfDocxByDefault_generated", docBytes);

        assertSvgRasterizedToPng(docBytes);
        assertEquals(List.of(), svgFiles(unzip(docBytes)), "Without embedSvg a picture is a PNG only");
    }

    @Test
    void embedSvgPutsSvgNextToPng() throws Exception {
        byte[] docBytes = exportToDOCX(htmlWithSvgImage(SIMPLE_SVG), readTemplate("reference_template"), PandocParams.builder().embedSvg(true).build());
        writeReportDocx("embedSvgPutsSvgNextToPng_generated", docBytes);

        assertSvgRasterizedToPng(docBytes);
        assertEquals(1, svgFiles(unzip(docBytes)).size(), "With embedSvg the picture carries its SVG");
    }

    private static List<String> svgFiles(Map<String, byte[]> entries) {
        return entries.keySet().stream().filter(name -> name.startsWith("word/media/") && name.toLowerCase().endsWith(".svg")).toList();
    }

    private static String htmlWithSvgImage(String svg) {
        String dataUrl = "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
        return "<html><head><title>SVG Test</title></head><body>"
                + "<p>Diagram below:</p>"
                + "<img src=\"" + dataUrl + "\" />"
                + "<p>After diagram.</p>"
                + "</body></html>";
    }

    private void assertSvgRasterizedToPng(byte[] docBytes) throws Exception {
        Map<String, byte[]> entries = unzip(docBytes);
        Map<String, String> targets = relationshipTargets(entries.get("word/_rels/document.xml.rels"));
        byte[] documentXml = entries.get("word/document.xml");
        assertNotNull(documentXml, "word/document.xml should be present in the DOCX");

        NodeList blips = parse(documentXml).getElementsByTagNameNS(DRAWING_NS, "blip");
        assertTrue(blips.getLength() > 0, "document.xml should reference the embedded image via <a:blip>");
        Set<String> svgParts = new HashSet<>();
        for (int i = 0; i < blips.getLength(); i++) {
            Element blip = (Element) blips.item(i);
            String png = "word/" + targets.get(blip.getAttributeNS(RELATIONSHIPS_NS, "embed"));
            assertTrue(png.toLowerCase().endsWith(".png") && entries.containsKey(png), "A picture shows a rasterized PNG, found: " + png);
            // weasysvg adds the SVG of the picture as its svgBlip, which holds only what Word draws
            NodeList svgBlips = blip.getElementsByTagNameNS(SVG_NS, "svgBlip");
            for (int j = 0; j < svgBlips.getLength(); j++) {
                String svgPart = "word/" + targets.get(((Element) svgBlips.item(j)).getAttributeNS(RELATIONSHIPS_NS, "embed"));
                assertTrue(svgPart.toLowerCase().endsWith(".svg") && entries.containsKey(svgPart), "An svgBlip names an SVG part, found: " + svgPart);
                String svg = new String(entries.get(svgPart), StandardCharsets.UTF_8);
                assertFalse(svg.contains("foreignObject"), svgPart + " should not contain a foreignObject");
                assertFalse(svg.contains("filter="), svgPart + " should not contain a filter");
                assertFalse(svg.contains("<mask"), svgPart + " should not contain a mask");
                svgParts.add(svgPart);
            }
        }
        assertEquals(Set.copyOf(svgFiles(entries)), svgParts, "Every SVG in word/media/ is the svgBlip of a picture");
    }

    private static Map<String, String> relationshipTargets(byte[] rels) throws Exception {
        assertNotNull(rels, "word/_rels/document.xml.rels should be present in the DOCX");
        Map<String, String> targets = new HashMap<>();
        NodeList relationships = parse(rels).getElementsByTagNameNS(PACKAGE_RELATIONSHIPS_NS, "Relationship");
        for (int i = 0; i < relationships.getLength(); i++) {
            Element relationship = (Element) relationships.item(i);
            targets.put(relationship.getAttribute("Id"), relationship.getAttribute("Target"));
        }
        return targets;
    }

    private static Document parse(byte[] xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
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
