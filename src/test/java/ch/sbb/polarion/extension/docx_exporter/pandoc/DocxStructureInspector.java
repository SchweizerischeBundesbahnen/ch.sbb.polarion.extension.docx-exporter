package ch.sbb.polarion.extension.docx_exporter.pandoc;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Read-only structural view over a generated DOCX's {@code word/document.xml}, built with a plain
 * namespace-aware JAXP DOM parse (no docx4j/POI accessor quirks). It exposes exactly the pieces the
 * pandoc integration tests assert on: the ordered sequence of formatted text segments per paragraph,
 * image drawing extents, and table structure (width, alignment, cells and merges).
 * <p>
 * pandoc emits one run per word (spaces as their own runs), all sharing identical {@code <w:rPr>};
 * {@link #paragraphs(byte[])} merges adjacent runs with equal {@link RunFormat} so the resulting
 * {@link Segment}s line up with the visually-distinct formatted spans.
 */
final class DocxStructureInspector {

    private static final String W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String WP_NS = "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing";

    private DocxStructureInspector() {
    }

    /** Core visual run formatting (font-family intentionally excluded). Defaults represent "unset". */
    record RunFormat(boolean bold, boolean italic, boolean underline, boolean strike,
                     String vertAlign, String color, String highlight, int sizeHalfPoints) {

        static final RunFormat PLAIN = new RunFormat(false, false, false, false, null, null, null, 0);
    }

    /**
     * Mutable fluent builder for expected {@link RunFormat}s in tests — a separate type so the chainable
     * setters ({@code bold()}, {@code italic()}, …) don't collide with {@link RunFormat}'s record accessors.
     */
    static final class Fmt {
        private boolean bold;
        private boolean italic;
        private boolean underline;
        private boolean strike;
        private String vertAlign;
        private String color;
        private String highlight;
        private int sizeHalfPoints;

        Fmt bold() {
            this.bold = true;
            return this;
        }

        Fmt italic() {
            this.italic = true;
            return this;
        }

        Fmt underline() {
            this.underline = true;
            return this;
        }

        Fmt strike() {
            this.strike = true;
            return this;
        }

        Fmt superscript() {
            this.vertAlign = "superscript";
            return this;
        }

        Fmt subscript() {
            this.vertAlign = "subscript";
            return this;
        }

        Fmt color(String value) {
            this.color = value;
            return this;
        }

        Fmt highlight(String value) {
            this.highlight = value;
            return this;
        }

        Fmt size(int halfPoints) {
            this.sizeHalfPoints = halfPoints;
            return this;
        }

        RunFormat build() {
            return new RunFormat(bold, italic, underline, strike, vertAlign, color, highlight, sizeHalfPoints);
        }
    }

    record Segment(RunFormat format, String text) {
    }

    record Extent(long cx, long cy) {
    }

    record Paragraph(String styleId, List<Segment> segments, List<Extent> imageExtents) {
    }

    record Cell(String text, String vMerge, Integer gridSpan) {
    }

    record Table(String widthType, long width, String jc, List<List<Cell>> rows) {
    }

    /** Starts a fluent formatting spec for building an expected segment. */
    static Fmt fmt() {
        return new Fmt();
    }

    /** Convenience factory for a plain (unformatted) segment. */
    static Segment plain(String text) {
        return new Segment(RunFormat.PLAIN, text);
    }

    /** Convenience factory for a formatted segment. */
    static Segment seg(Fmt format, String text) {
        return new Segment(format.build(), text);
    }

    static List<Paragraph> paragraphs(byte[] docx) throws IOException {
        Element body = body(docx);
        List<Paragraph> result = new ArrayList<>();
        for (Element p : children(body, "p")) {
            result.add(toParagraph(p));
        }
        return result;
    }

    static List<Table> tables(byte[] docx) throws IOException {
        Element body = body(docx);
        List<Table> result = new ArrayList<>();
        NodeList tbls = body.getElementsByTagNameNS(W_NS, "tbl");
        for (int i = 0; i < tbls.getLength(); i++) {
            result.add(toTable((Element) tbls.item(i)));
        }
        return result;
    }

    private static Paragraph toParagraph(Element p) {
        String styleId = null;
        Element pPr = child(p, "pPr");
        if (pPr != null) {
            Element pStyle = child(pPr, "pStyle");
            styleId = wAttr(pStyle, "val");
        }

        List<Segment> segments = new ArrayList<>();
        for (Element run : children(p, "r")) {
            if (run.getElementsByTagNameNS(W_NS, "t").getLength() == 0) {
                continue; // non-text run (e.g. an image drawing) — captured via extents instead
            }
            RunFormat format = runFormat(child(run, "rPr"));
            String text = textOf(run);
            if (!segments.isEmpty() && segments.get(segments.size() - 1).format().equals(format)) {
                Segment previous = segments.remove(segments.size() - 1);
                segments.add(new Segment(format, previous.text() + text));
            } else {
                segments.add(new Segment(format, text));
            }
        }

        List<Extent> extents = new ArrayList<>();
        NodeList extentNodes = p.getElementsByTagNameNS(WP_NS, "extent");
        for (int i = 0; i < extentNodes.getLength(); i++) {
            Element extent = (Element) extentNodes.item(i);
            extents.add(new Extent(
                    Long.parseLong(extent.getAttribute("cx")),
                    Long.parseLong(extent.getAttribute("cy"))));
        }

        return new Paragraph(styleId, segments, extents);
    }

    private static RunFormat runFormat(Element rPr) {
        if (rPr == null) {
            return RunFormat.PLAIN;
        }
        boolean bold = isToggleOn(child(rPr, "b"));
        boolean italic = isToggleOn(child(rPr, "i"));
        boolean strike = isToggleOn(child(rPr, "strike"));

        Element u = child(rPr, "u");
        boolean underline = u != null && !"none".equalsIgnoreCase(wAttr(u, "val"));

        String vertAlign = null;
        Element va = child(rPr, "vertAlign");
        if (va != null) {
            String value = wAttr(va, "val");
            if ("superscript".equals(value) || "subscript".equals(value)) {
                vertAlign = value;
            }
        }

        String color = normalizeColor(wAttr(child(rPr, "color"), "val"));
        String highlight = normalizeColor(wAttr(child(rPr, "shd"), "fill"));

        int size = 0;
        String sz = wAttr(child(rPr, "sz"), "val");
        if (sz != null && !"22".equals(sz)) { // 22 half-points = 11pt, the document default
            size = Integer.parseInt(sz);
        }

        return new RunFormat(bold, italic, underline, strike, vertAlign, color, highlight, size);
    }

    private static Table toTable(Element tbl) {
        String widthType = null;
        long width = 0;
        String jc = "left";
        Element tblPr = child(tbl, "tblPr");
        if (tblPr != null) {
            Element tblW = child(tblPr, "tblW");
            if (tblW != null) {
                widthType = wAttr(tblW, "type");
                String w = wAttr(tblW, "w");
                if (w != null) {
                    width = Long.parseLong(w);
                }
            }
            Element jcElement = child(tblPr, "jc");
            if (jcElement != null && wAttr(jcElement, "val") != null) {
                jc = wAttr(jcElement, "val");
            }
        }

        List<List<Cell>> rows = new ArrayList<>();
        for (Element tr : children(tbl, "tr")) {
            List<Cell> cells = new ArrayList<>();
            for (Element tc : children(tr, "tc")) {
                Element tcPr = child(tc, "tcPr");
                String vMerge = null;
                Integer gridSpan = null;
                if (tcPr != null) {
                    Element vMergeElement = child(tcPr, "vMerge");
                    if (vMergeElement != null) {
                        String value = wAttr(vMergeElement, "val");
                        vMerge = value != null ? value : "continue";
                    }
                    String gridSpanValue = wAttr(child(tcPr, "gridSpan"), "val");
                    if (gridSpanValue != null) {
                        gridSpan = Integer.parseInt(gridSpanValue);
                    }
                }
                cells.add(new Cell(textOf(tc), vMerge, gridSpan));
            }
            rows.add(cells);
        }
        return new Table(widthType, width, jc, rows);
    }

    private static boolean isToggleOn(Element toggle) {
        if (toggle == null) {
            return false;
        }
        String value = wAttr(toggle, "val");
        return value == null || !("false".equalsIgnoreCase(value) || "0".equals(value));
    }

    private static String normalizeColor(String value) {
        if (value == null || "auto".equalsIgnoreCase(value)
                || "000000".equalsIgnoreCase(value) || "FFFFFF".equalsIgnoreCase(value)) {
            return null;
        }
        return value.toUpperCase();
    }

    private static Element body(byte[] docx) throws IOException {
        byte[] documentXml = unzip(docx).get("word/document.xml");
        if (documentXml == null) {
            throw new IOException("word/document.xml not found in DOCX");
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(documentXml));
            return (Element) document.getElementsByTagNameNS(W_NS, "body").item(0);
        } catch (Exception e) {
            throw new IOException("Failed to parse word/document.xml", e);
        }
    }

    private static Map<String, byte[]> unzip(byte[] docx) throws IOException {
        Map<String, byte[]> entries = new java.util.LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(docx))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries.put(entry.getName(), zis.readAllBytes());
            }
        }
        return entries;
    }

    private static List<Element> children(Element parent, String localName) {
        List<Element> out = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE
                    && W_NS.equals(node.getNamespaceURI())
                    && localName.equals(node.getLocalName())) {
                out.add((Element) node);
            }
        }
        return out;
    }

    private static Element child(Element parent, String localName) {
        if (parent == null) {
            return null;
        }
        List<Element> found = children(parent, localName);
        return found.isEmpty() ? null : found.get(0);
    }

    private static String wAttr(Element element, String localName) {
        if (element == null) {
            return null;
        }
        String value = element.getAttributeNS(W_NS, localName);
        return value == null || value.isEmpty() ? null : value;
    }

    private static String textOf(Element element) {
        StringBuilder sb = new StringBuilder();
        NodeList texts = element.getElementsByTagNameNS(W_NS, "t");
        for (int i = 0; i < texts.getLength(); i++) {
            sb.append(texts.item(i).getTextContent());
        }
        return sb.toString();
    }
}
