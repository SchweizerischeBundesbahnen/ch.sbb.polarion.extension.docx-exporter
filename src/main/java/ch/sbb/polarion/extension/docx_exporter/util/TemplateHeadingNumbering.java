package ch.sbb.polarion.extension.docx_exporter.util;

import com.polarion.core.util.logging.Logger;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Tells which heading levels a DOCX template numbers by itself, i.e. which of its "heading 1".."heading 9" styles
 * are linked to a numbering definition. Pandoc gives a heading of level N the style named "heading N",
 * so a heading of such a level gets its number from Word, and the number Polarion writes into its text would be shown twice.
 */
@UtilityClass
public class TemplateHeadingNumbering {

    private static final Logger logger = Logger.getLogger(TemplateHeadingNumbering.class);

    private static final String W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String STYLES_ENTRY = "word/styles.xml";
    private static final String NUMBERING_ENTRY = "word/numbering.xml";
    private static final String HEADING_STYLE_NAME_PREFIX = "heading ";
    private static final String VAL = "val";
    private static final String NO_NUMBERING = "0";
    private static final String NUMBER_FORMAT_NONE = "none";
    private static final int MAX_HEADING_LEVEL = 9;
    // Guards against a template with a cycle in its basedOn chain
    private static final int MAX_STYLE_INHERITANCE_DEPTH = 32;

    /**
     * @return heading levels (1-9) whose heading style is numbered in the template, empty if none is or the template cannot be read
     */
    public @NotNull Set<Integer> getNumberedHeadingLevels(byte @Nullable [] template) {
        if (template == null || template.length == 0) {
            return Set.of();
        }
        try {
            Map<String, byte[]> entries = readEntries(template);
            byte[] styles = entries.get(STYLES_ENTRY);
            byte[] numbering = entries.get(NUMBERING_ENTRY);
            if (styles == null || numbering == null) {
                return Set.of();
            }
            return getNumberedHeadingLevels(parse(styles), parse(numbering));
        } catch (Exception e) {
            logger.warn("Could not read heading numbering of the DOCX template, heading numbers are left as they are", e);
            return Set.of();
        }
    }

    private @NotNull Set<Integer> getNumberedHeadingLevels(@NotNull Document styles, @NotNull Document numbering) {
        Map<String, StyleInfo> stylesById = readStyles(styles);
        Numbering numberingInfo = readNumbering(numbering);

        Set<Integer> numberedLevels = new TreeSet<>();
        for (StyleInfo style : stylesById.values()) {
            Integer headingLevel = getHeadingLevel(style.name());
            if (headingLevel != null && isNumbered(style, stylesById, numberingInfo)) {
                numberedLevels.add(headingLevel);
            }
        }
        return numberedLevels;
    }

    private boolean isNumbered(@NotNull StyleInfo style, @NotNull Map<String, StyleInfo> stylesById, @NotNull Numbering numbering) {
        // Numbering of a style is inherited along its basedOn chain, the nearest style which defines it wins
        StyleInfo current = style;
        for (int depth = 0; current != null && depth < MAX_STYLE_INHERITANCE_DEPTH; depth++) {
            if (current.numId() != null) {
                return !NO_NUMBERING.equals(current.numId()) && numbering.showsNumber(current.numId(), levelOf(current, style, numbering));
            }
            current = current.basedOn() == null ? null : stylesById.get(current.basedOn());
        }
        // A level of a list can also be linked to the style from the numbering side
        return numbering.isLinkedToStyle(style.id());
    }

    private @Nullable String levelOf(@NotNull StyleInfo numberedStyle, @NotNull StyleInfo headingStyle, @NotNull Numbering numbering) {
        if (numberedStyle.ilvl() != null) {
            return numberedStyle.ilvl();
        }
        String linkedLevel = numbering.levelLinkedToStyle(numberedStyle.numId(), headingStyle.id());
        return linkedLevel != null ? linkedLevel : NO_NUMBERING;
    }

    private @Nullable Integer getHeadingLevel(@Nullable String styleName) {
        // Styles are matched by name, which Word keeps in English whatever the language of the template (the ID is localized, e.g. "berschrift1")
        if (styleName == null) {
            return null;
        }
        String name = styleName.toLowerCase(Locale.ROOT);
        if (!name.startsWith(HEADING_STYLE_NAME_PREFIX)) {
            return null;
        }
        try {
            int level = Integer.parseInt(name.substring(HEADING_STYLE_NAME_PREFIX.length()).trim());
            return level >= 1 && level <= MAX_HEADING_LEVEL ? level : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private @NotNull Map<String, StyleInfo> readStyles(@NotNull Document styles) {
        Map<String, StyleInfo> result = new HashMap<>();
        NodeList styleElements = styles.getElementsByTagNameNS(W_NS, "style");
        for (int i = 0; i < styleElements.getLength(); i++) {
            Element style = (Element) styleElements.item(i);
            String id = style.getAttributeNS(W_NS, "styleId");
            Element numPr = firstChild(firstChild(style, "pPr"), "numPr");
            result.put(id, new StyleInfo(
                    id,
                    childVal(style, "name"),
                    childVal(style, "basedOn"),
                    childVal(numPr, "numId"),
                    childVal(numPr, "ilvl")));
        }
        return result;
    }

    private @NotNull Numbering readNumbering(@NotNull Document numbering) {
        Map<String, Element> abstractNums = new HashMap<>();
        NodeList abstractNumElements = numbering.getElementsByTagNameNS(W_NS, "abstractNum");
        for (int i = 0; i < abstractNumElements.getLength(); i++) {
            Element abstractNum = (Element) abstractNumElements.item(i);
            abstractNums.put(abstractNum.getAttributeNS(W_NS, "abstractNumId"), abstractNum);
        }

        Map<String, Element> abstractNumByNumId = new HashMap<>();
        NodeList numElements = numbering.getElementsByTagNameNS(W_NS, "num");
        for (int i = 0; i < numElements.getLength(); i++) {
            Element num = (Element) numElements.item(i);
            Element abstractNum = abstractNums.get(childVal(num, "abstractNumId"));
            if (abstractNum != null) {
                abstractNumByNumId.put(num.getAttributeNS(W_NS, "numId"), abstractNum);
            }
        }
        return new Numbering(abstractNumByNumId);
    }

    private record StyleInfo(@NotNull String id, @Nullable String name, @Nullable String basedOn, @Nullable String numId, @Nullable String ilvl) {
    }

    private record Numbering(@NotNull Map<String, Element> abstractNumByNumId) {

        boolean showsNumber(@NotNull String numId, @Nullable String ilvl) {
            Element abstractNum = abstractNumByNumId.get(numId);
            if (abstractNum == null) {
                return false;
            }
            Element level = findLevel(abstractNum, ilvl == null ? NO_NUMBERING : ilvl);
            // A level without an explicit format is decimal, one with the format "none" shows no number
            return level == null || !NUMBER_FORMAT_NONE.equals(childVal(level, "numFmt"));
        }

        @Nullable String levelLinkedToStyle(@NotNull String numId, @NotNull String styleId) {
            Element abstractNum = abstractNumByNumId.get(numId);
            Element level = abstractNum == null ? null : findLevelLinkedTo(abstractNum, styleId);
            return level == null ? null : level.getAttributeNS(W_NS, "ilvl");
        }

        boolean isLinkedToStyle(@NotNull String styleId) {
            Set<Element> distinctAbstractNums = new HashSet<>(abstractNumByNumId.values());
            for (Element abstractNum : distinctAbstractNums) {
                Element level = findLevelLinkedTo(abstractNum, styleId);
                if (level != null && !NUMBER_FORMAT_NONE.equals(childVal(level, "numFmt"))) {
                    return true;
                }
            }
            return false;
        }

        private static @Nullable Element findLevel(@NotNull Element abstractNum, @NotNull String ilvl) {
            NodeList levels = abstractNum.getElementsByTagNameNS(W_NS, "lvl");
            for (int i = 0; i < levels.getLength(); i++) {
                Element level = (Element) levels.item(i);
                if (ilvl.equals(level.getAttributeNS(W_NS, "ilvl"))) {
                    return level;
                }
            }
            return null;
        }

        private static @Nullable Element findLevelLinkedTo(@NotNull Element abstractNum, @NotNull String styleId) {
            NodeList levels = abstractNum.getElementsByTagNameNS(W_NS, "lvl");
            for (int i = 0; i < levels.getLength(); i++) {
                Element level = (Element) levels.item(i);
                if (styleId.equals(childVal(level, "pStyle"))) {
                    return level;
                }
            }
            return null;
        }
    }

    private static @Nullable Element firstChild(@Nullable Element parent, @NotNull String localName) {
        if (parent == null) {
            return null;
        }
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element child && W_NS.equals(child.getNamespaceURI()) && localName.equals(child.getLocalName())) {
                return child;
            }
        }
        return null;
    }

    private static @Nullable String childVal(@Nullable Element parent, @NotNull String localName) {
        Element child = firstChild(parent, localName);
        if (child == null || !child.hasAttributeNS(W_NS, VAL)) {
            return null;
        }
        return child.getAttributeNS(W_NS, VAL);
    }

    private @NotNull Map<String, byte[]> readEntries(byte @NotNull [] template) throws IOException {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(template))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (STYLES_ENTRY.equals(entry.getName()) || NUMBERING_ENTRY.equals(entry.getName())) {
                    entries.put(entry.getName(), zip.readAllBytes());
                }
            }
        }
        return entries;
    }

    private @NotNull Document parse(byte @NotNull [] xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        // The template is uploaded by users: no DTDs and no external entities
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setExpandEntityReferences(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(xml));
    }
}
