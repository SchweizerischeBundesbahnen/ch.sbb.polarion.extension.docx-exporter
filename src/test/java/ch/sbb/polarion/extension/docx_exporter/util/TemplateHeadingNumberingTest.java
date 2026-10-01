package ch.sbb.polarion.extension.docx_exporter.util;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateHeadingNumberingTest {

    private static final String W = "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"";

    // A multilevel list whose levels 0 and 1 are decimal and level 2 shows no number
    private static final String NUMBERING = "<w:numbering " + W + ">"
            + "<w:abstractNum w:abstractNumId=\"7\">"
            + "<w:lvl w:ilvl=\"0\"><w:numFmt w:val=\"decimal\"/><w:lvlText w:val=\"%1\"/></w:lvl>"
            + "<w:lvl w:ilvl=\"1\"><w:numFmt w:val=\"decimal\"/><w:lvlText w:val=\"%1.%2\"/></w:lvl>"
            + "<w:lvl w:ilvl=\"2\"><w:numFmt w:val=\"none\"/><w:lvlText w:val=\"\"/></w:lvl>"
            + "</w:abstractNum>"
            + "<w:num w:numId=\"5\"><w:abstractNumId w:val=\"7\"/></w:num>"
            + "</w:numbering>";

    @Test
    void numberedHeadingStyles() {
        byte[] template = template(styles(
                headingStyle("Heading1", "heading 1", numPr("5", "0")),
                headingStyle("Heading2", "heading 2", numPr("5", "1")),
                headingStyle("Heading3", "heading 3", "")), NUMBERING);

        assertEquals(Set.of(1, 2), TemplateHeadingNumbering.getNumberedHeadingLevels(template));
    }

    @Test
    void headingStylesWithoutNumbering() {
        byte[] template = template(styles(
                headingStyle("Heading1", "heading 1", ""),
                headingStyle("Heading2", "heading 2", "")), NUMBERING);

        assertEquals(Set.of(), TemplateHeadingNumbering.getNumberedHeadingLevels(template));
    }

    @Test
    void headingStylesAreMatchedByNameNotByLocalizedId() {
        byte[] template = template(styles(headingStyle("berschrift1", "heading 1", numPr("5", "0"))), NUMBERING);

        assertEquals(Set.of(1), TemplateHeadingNumbering.getNumberedHeadingLevels(template));
    }

    @Test
    void numberingIsInheritedAlongBasedOn() {
        byte[] template = template(styles(
                style("NumberedBase", "Numbered base", null, numPr("5", "0")),
                style("Heading1", "heading 1", "NumberedBase", ""),
                // a style of its own which switches the inherited numbering off
                style("Heading2", "heading 2", "NumberedBase", numPr("0", null))), NUMBERING);

        assertEquals(Set.of(1), TemplateHeadingNumbering.getNumberedHeadingLevels(template));
    }

    @Test
    void levelsLinkedToHeadingStylesFromTheNumberingSide() {
        String numbering = "<w:numbering " + W + ">"
                + "<w:abstractNum w:abstractNumId=\"1\">"
                + "<w:lvl w:ilvl=\"0\"><w:numFmt w:val=\"decimal\"/><w:pStyle w:val=\"Heading1\"/></w:lvl>"
                + "<w:lvl w:ilvl=\"1\"><w:numFmt w:val=\"none\"/><w:pStyle w:val=\"Heading2\"/></w:lvl>"
                + "</w:abstractNum>"
                + "<w:num w:numId=\"3\"><w:abstractNumId w:val=\"1\"/></w:num>"
                + "</w:numbering>";
        byte[] template = template(styles(
                headingStyle("Heading1", "heading 1", ""),
                headingStyle("Heading2", "heading 2", "")), numbering);

        assertEquals(Set.of(1), TemplateHeadingNumbering.getNumberedHeadingLevels(template));
    }

    @Test
    void levelWithoutNumberAndUnknownNumberingShowNoNumber() {
        byte[] template = template(styles(
                // level 2 of the list has the format "none"
                headingStyle("Heading1", "heading 1", numPr("5", "2")),
                // there is no numbering with this ID
                headingStyle("Heading2", "heading 2", numPr("42", "0"))), NUMBERING);

        assertEquals(Set.of(), TemplateHeadingNumbering.getNumberedHeadingLevels(template));
    }

    @Test
    void levelOverriddenByTheListInstance() {
        String numbering = "<w:numbering " + W + ">"
                + "<w:abstractNum w:abstractNumId=\"7\">"
                + "<w:lvl w:ilvl=\"0\"><w:numFmt w:val=\"decimal\"/></w:lvl>"
                + "<w:lvl w:ilvl=\"1\"><w:numFmt w:val=\"decimal\"/></w:lvl>"
                + "</w:abstractNum>"
                // the instance redefines level 1 to show no number, and only restarts level 0
                + "<w:num w:numId=\"5\"><w:abstractNumId w:val=\"7\"/>"
                + "<w:lvlOverride w:ilvl=\"0\"><w:startOverride w:val=\"3\"/></w:lvlOverride>"
                + "<w:lvlOverride w:ilvl=\"1\"><w:lvl w:ilvl=\"1\"><w:numFmt w:val=\"none\"/><w:lvlText w:val=\"\"/></w:lvl></w:lvlOverride>"
                + "</w:num>"
                + "</w:numbering>";
        byte[] template = template(styles(
                headingStyle("Heading1", "heading 1", numPr("5", "0")),
                headingStyle("Heading2", "heading 2", numPr("5", "1"))), numbering);

        assertEquals(Set.of(1), TemplateHeadingNumbering.getNumberedHeadingLevels(template));
    }

    @Test
    void levelTheListDoesNotDefineShowsNoNumber() {
        byte[] template = template(styles(headingStyle("Heading1", "heading 1", numPr("5", "4"))), NUMBERING);

        assertEquals(Set.of(), TemplateHeadingNumbering.getNumberedHeadingLevels(template));
    }

    @Test
    void entryExpandingBeyondTheLimitIsNotRead() {
        // Spaces compress to almost nothing, the entry is far smaller than the template size limit but expands to 11 MB
        String hugeStyles = styles(headingStyle("Heading1", "heading 1", numPr("5", "0"))).replace("</w:styles>", " ".repeat(11 * 1024 * 1024) + "</w:styles>");
        byte[] template = template(hugeStyles, NUMBERING);

        assertTrue(template.length < 100 * 1024);
        assertEquals(Set.of(), TemplateHeadingNumbering.getNumberedHeadingLevels(template));
    }

    @Test
    void levelDefaultsToTheFirstOne() {
        byte[] template = template(styles(headingStyle("Heading1", "heading 1", numPr("5", null))), NUMBERING);

        assertEquals(Set.of(1), TemplateHeadingNumbering.getNumberedHeadingLevels(template));
    }

    @Test
    void noOrUnreadableTemplate() {
        assertEquals(Set.of(), TemplateHeadingNumbering.getNumberedHeadingLevels(null));
        assertEquals(Set.of(), TemplateHeadingNumbering.getNumberedHeadingLevels(new byte[0]));
        assertEquals(Set.of(), TemplateHeadingNumbering.getNumberedHeadingLevels("not a docx".getBytes(StandardCharsets.UTF_8)));
        // no numbering part at all
        assertEquals(Set.of(), TemplateHeadingNumbering.getNumberedHeadingLevels(template(styles(headingStyle("Heading1", "heading 1", numPr("5", "0"))), null)));
        // a DOCTYPE is refused, so is the template
        assertEquals(Set.of(), TemplateHeadingNumbering.getNumberedHeadingLevels(template("<!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><w:styles " + W + "/>", NUMBERING)));
    }

    @Test
    @SneakyThrows
    void referenceTemplateOfTheTests() {
        // The default reference template of pandoc, whose heading styles are not numbered
        try (InputStream template = getClass().getResourceAsStream("/pandoc/templates/reference_template.docx")) {
            assertEquals(Set.of(), TemplateHeadingNumbering.getNumberedHeadingLevels(template.readAllBytes()));
        }
    }

    private static String styles(String... styles) {
        return "<w:styles " + W + ">" + String.join("", styles) + "</w:styles>";
    }

    private static String headingStyle(String id, String name, String numPr) {
        return style(id, name, null, numPr);
    }

    private static String style(String id, String name, String basedOn, String numPr) {
        return "<w:style w:type=\"paragraph\" w:styleId=\"" + id + "\"><w:name w:val=\"" + name + "\"/>"
                + (basedOn == null ? "" : "<w:basedOn w:val=\"" + basedOn + "\"/>")
                + "<w:pPr><w:keepNext/>" + numPr + "<w:outlineLvl w:val=\"0\"/></w:pPr></w:style>";
    }

    private static String numPr(String numId, String ilvl) {
        return "<w:numPr>" + (ilvl == null ? "" : "<w:ilvl w:val=\"" + ilvl + "\"/>") + "<w:numId w:val=\"" + numId + "\"/></w:numPr>";
    }

    @SneakyThrows
    private static byte[] template(String styles, String numbering) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("word/styles.xml"));
            zip.write(styles.getBytes(StandardCharsets.UTF_8));
            if (numbering != null) {
                zip.putNextEntry(new ZipEntry("word/numbering.xml"));
                zip.write(numbering.getBytes(StandardCharsets.UTF_8));
            }
        }
        return out.toByteArray();
    }
}
