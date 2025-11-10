package ch.sbb.polarion.extension.docx_exporter.util;

import ch.sbb.polarion.extension.docx_exporter.constants.CssProp;
import ch.sbb.polarion.extension.docx_exporter.constants.HtmlTag;
import ch.sbb.polarion.extension.docx_exporter.constants.HtmlTagAttr;
import ch.sbb.polarion.extension.generic.regex.IRegexEngine;
import ch.sbb.polarion.extension.generic.regex.RegexMatcher;
import ch.sbb.polarion.extension.generic.settings.NamedSettings;
import ch.sbb.polarion.extension.generic.settings.SettingId;
import ch.sbb.polarion.extension.generic.util.HtmlUtils;
import ch.sbb.polarion.extension.docx_exporter.rest.model.conversion.ExportParams;
import ch.sbb.polarion.extension.docx_exporter.service.DocxExporterPolarionService;
import ch.sbb.polarion.extension.docx_exporter.settings.LocalizationSettings;
import ch.sbb.polarion.extension.docx_exporter.util.exporter.CustomPageBreakPart;
import ch.sbb.polarion.extension.docx_exporter.util.html.HtmlLinksHelper;
import com.polarion.alm.shared.util.StringUtils;
import com.polarion.core.boot.PolarionProperties;
import com.polarion.core.config.Configuration;
import com.polarion.core.util.xml.CSSStyle;
import com.steadystate.css.parser.CSSOMParser;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Comment;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;
import org.w3c.dom.css.CSSStyleDeclaration;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static ch.sbb.polarion.extension.docx_exporter.util.exporter.Constants.*;

public class HtmlProcessor {

    private static final int A4_PORTRAIT_WIDTH = 592;
    private static final int A4_PORTRAIT_HEIGHT = 874;
    private static final int FULL_WIDTH_PERCENT = 100;
    private static final float EX_TO_PX_RATIO = 6.5F;
    private static final String MEASURE_PX = "px";
    private static final String MEASURE_EX = "ex";
    private static final String MEASURE_PERCENT = "%";
    private static final String TABLE_OPEN_TAG = "<table";
    private static final String TABLE_END_TAG = "</table>";
    private static final String TABLE_ROW_OPEN_TAG = "<tr";
    private static final String TABLE_ROW_END_TAG = "</tr>";
    private static final String TABLE_COLUMN_OPEN_TAG = "<td";
    private static final String TABLE_COLUMN_END_TAG = "</td>";
    private static final String DIV_START_TAG = "<div>";
    private static final String DIV_END_TAG = "</div>";
    private static final String SPAN = "span";
    private static final String SPAN_END_TAG = "</span>";
    private static final String COMMENT_START = "[span";
    private static final String COMMENT_END = "[/span]";
    private static final String WIDTH = "width";
    private static final String MEASURE = "measure";
    private static final String MEASURE_WIDTH = "measureWidth";
    private static final String HEIGHT = "height";
    private static final String MEASURE_HEIGHT = "measureHeight";
    private static final String NUMBER = "number";
    private static final String CLASS = "class";
    private static final String COMMENT_START_CLASS = "comment-start";
    private static final String COMMENT_END_CLASS = "comment-end";
    private static final String META = "meta";
    private static final String AUTHOR = "author";
    private static final String DATE = "date";
    private static final String TEXT = "text";
    private static final String DOLLAR_SIGN = "$";
    private static final String DOLLAR_ENTITY = "&dollar;";
    private static final String EMPTY_FIELD_TITLE = "This field is empty";
    private static final String COMMA_SEPARATOR = ", ";

    private static final String LOCALHOST = "localhost";
    public static final String HTTP_PROTOCOL_PREFIX = "http://";
    public static final String HTTPS_PROTOCOL_PREFIX = "https://";

    private final FileResourceProvider fileResourceProvider;
    private final LocalizationSettings localizationSettings;
    private final HtmlLinksHelper httpLinksHelper;
    private final DocxExporterPolarionService docxExporterPolarionService;
    private final @NotNull CSSOMParser parser = new CSSOMParser();

    public HtmlProcessor(FileResourceProvider fileResourceProvider, LocalizationSettings localizationSettings, HtmlLinksHelper httpLinksHelper, DocxExporterPolarionService docxExporterPolarionService) {
        this.fileResourceProvider = fileResourceProvider;
        this.localizationSettings = localizationSettings;
        this.httpLinksHelper = httpLinksHelper;
        this.docxExporterPolarionService = docxExporterPolarionService;
    }

    public String processHtmlForPDF(@NotNull String html, @NotNull ExportParams exportParams, @NotNull List<String> selectedRoleEnumValues) {
        // I. FIRST SECTION - manipulate HTML as a String. These changes are either not possible or not made easier with JSoup
        // ----------------

        // Replace all dollar-characters in HTML document before applying any regular expressions, as it has special meaning there
        html = encodeDollarSigns(html);

        // Remove all <pd4ml:page> tags which only have meaning for PD4ML library which we are not using.
        html = removePd4mlPageTags(html);

        // Change path of enum images from internal Polarion to publicly available
        html = html.replace("/ria/images/enums/", "/icons/default/enums/");

        //Was noticed that some externally imported/pasted elements (at this moment tables) contain strange extra block like
        //<div style="clear:both;"> with the duplicated content inside.
        //Potential fix below is simple: just hide these blocks.
        html = html.replace("style=\"clear:both;\"", "style=\"clear:both;display:none;\"");

        // fix HTML adding closing tag for <pd4ml:toc> - JSoup requires it
        html = html.replaceAll("(<pd4ml:toc[^>]*)(>)", "$1></pd4ml:toc>");

        if (exportParams.getRenderComments() != null) {
            html = processComments(html);
        }

        // II. SECOND SECTION - manipulate HTML as a JSoup document. These changes are vice versa fulfilled easier with JSoup.
        // ----------------

        Document document = JSoupUtils.parseHtml(html);

        // From Polarion perspective h1 - is a document title, h2 are h1 heading etc. We are making such headings' uplifting here
        adjustDocumentHeadings(document);

        if (exportParams.isCutEmptyChapters()) {
            // Cut empty chapters if explicitly requested by user
            cutEmptyChapters(document);
        }
        if (exportParams.getChapters() != null) {
            // Leave only chapters explicitly selected by user
            cutNotNeededChapters(document, exportParams.getChapters());
        }

        removePageBreakAvoids(document);

        // Adjusts WorkItem attributes tables to stretch to full page width for better usage of page space and better readability.
        // Also changes absolute widths of normal table cells from absolute values to "auto" if "Fit tables and images to page" is on
        adjustCellWidth(document);

        if (exportParams.isCutEmptyWIAttributes()) {
            cutEmptyWIAttributes(document);
        }

        html = document.body().html();

        // III. THIRD SECTION - and finally again back to manipulating HTML as a String.
        // ----------------

        // Jsoup may convert &dollar; back to $ in some cases, so we need to replace it again
        html = encodeDollarSigns(html);

        html = adjustImageAlignmentForPDF(html);

        html = addTableOfFigures(addTableOfContent(html));
        html = replaceResourcesAsBase64Encoded(html);
        html = MediaUtils.removeSvgUnsupportedFeatureHint(html); //note that there is one more replacement attempt before replacing images with base64 representation
        html = properTableHeads(html);

        html = new NumberedListsSanitizer().fixNumberedLists(html);

        // ----
        // This sequence is important! We need first filter out Linked WorkItems and only then cut empty attributes,
        // cause after filtering Linked WorkItems can become empty. Also cutting local URLs should happen afterwards
        // as filtering workitems relies among other on anchors.
        if (!selectedRoleEnumValues.isEmpty()) {
            html = filterTabularLinkedWorkitems(html, selectedRoleEnumValues);
            html = filterNonTabularLinkedWorkitems(html, selectedRoleEnumValues);
        }
        if (exportParams.isCutLocalUrls()) {
            html = cutLocalUrls(html);
        }
        // ----

        html = localizeEnums(html, exportParams);

        if (hasCustomPageBreaks(html)) {
            //processPageBrakes contains its own adjustContentToFitPage() calls
            html = processPageBrakes(html);
        }
        html = adjustContentToFitPage(html);

        html = replaceLinks(html);

        html = html.replace("<p class=\"page_break\"></p>", "<p class=\"page_break\">&#12;</p>");

        if (!StringUtils.isEmptyTrimmed(exportParams.getRemovalSelector())) {
            html = clearSelectors(html, exportParams.getRemovalSelector());
        }

        // Do not change this entry order, '&nbsp;' can be used in the logic above, so we must cut them off as the last step
        html = cutExtraNbsp(html);
        return html;
    }

    /**
     * Escapes dollar signs in HTML to prevent them from being interpreted as regex special characters.
     * Should be called after Jsoup parsing operations that may convert &dollar; back to $.
     *
     * @param html HTML content to escape
     * @return HTML with dollar signs replaced by &dollar; entity
     */
    @NotNull
    private String encodeDollarSigns(@NotNull String html) {
        return html.replace(DOLLAR_SIGN, DOLLAR_ENTITY);
    }

    @NotNull
    private String removePd4mlPageTags(@NotNull String html) {
        return RegexMatcher.get("(<pd4ml:page.*>)(.)").replace(html, regexEngine -> regexEngine.group(2));
    }

    private void adjustDocumentHeadings(@NotNull Document document) {
        Elements headings = document.select("h1, h2, h3, h4, h5, h6");

        for (Element heading : headings) {
            if (JSoupUtils.isH1(heading)) {
                heading.tagName(HtmlTag.DIV);
                heading.addClass("title");
            } else {
                int level = heading.tagName().charAt(1) - '0';
                heading.tagName("h" + (level - 1));
            }
        }
    }

    @NotNull
    @VisibleForTesting
    Document cutEmptyChapters(@NotNull Document document) {
        // 'Empty chapter' is a heading tag which doesn't have any visible content "under it",
        // i.e. there are only not visible or whitespace elements between itself and next heading of same/higher level or end of parent/document.

        // Process from lowest to highest priority (h6 to h1), otherwise logic can be broken
        for (int headingLevel = H_TAG_MIN_PRIORITY; headingLevel >= 1; headingLevel--) {
            removeEmptyHeadings(document, headingLevel);
        }

        return document;
    }

    private void removeEmptyHeadings(@NotNull Document document, int headingLevel) {
        List<Element> headingsToRemove = JSoupUtils.selectEmptyHeadings(document, headingLevel);
        for (Element heading : headingsToRemove) {

            // In addition to removing heading itself, remove all following empty siblings until next heading, but not comments as they can have special meaning
            // We don't check additionally if sibling is empty, because if a heading was selected for removal there are only empty siblings under it
            Node nextSibling = heading.nextSibling();
            while (nextSibling != null) {
                if (JSoupUtils.isHeading(nextSibling)) {
                    break;
                } else {
                    Node siblingToRemove = nextSibling instanceof Comment ? null : nextSibling;
                    nextSibling = nextSibling.nextSibling();
                    if (siblingToRemove != null) {
                        siblingToRemove.remove();
                    }
                }
            }

            heading.remove();
        }
    }

    @VisibleForTesting
    void cutNotNeededChapters(@NotNull Document document, @NotNull List<String> selectedChapters) {
        List<ChapterInfo> chapters = getChaptersInfo(document, selectedChapters);

        // Process chapters to remove unwanted ones
        for (ChapterInfo currentChapter : chapters) {
            if (!currentChapter.shouldKeep()) {
                // Remember parent element for possible future usage
                Element parent = currentChapter.heading().parent();

                // Collect first 2 page break comments in the block to remove
                List<Comment> topPageBreakComments = collectPageBreakComments(currentChapter);

                Node nextChapterNode = removeChapter(currentChapter);

                // Re-insert page break comments at the position where the block was removed
                if (nextChapterNode != null) {
                    // Insert before the next H1
                    for (Comment pageBreak : topPageBreakComments) {
                        nextChapterNode.before(pageBreak);
                    }
                } else if (parent != null) {
                    // Otherwise insert at the end of parent
                    for (Comment pageBreak : topPageBreakComments) {
                        parent.appendChild(pageBreak);
                    }
                }
            }
        }
    }

    @NotNull
    private List<ChapterInfo> getChaptersInfo(@NotNull Document document, @NotNull List<String> selectedChapters) {
        List<ChapterInfo> chapters = new ArrayList<>();

        for (Element h1 : document.select(HtmlTag.H1)) {
            boolean shouldKeep = false;

            // Extract chapter number from the h1 structure: <h1><span><span>NUMBER</span></span>...</h1>
            Elements innerSpans = h1.select("span > span");
            if (!innerSpans.isEmpty()) {
                String chapterNumber = Objects.requireNonNull(innerSpans.first()).text();
                shouldKeep = selectedChapters.contains(chapterNumber);
            }
            chapters.add(new ChapterInfo(h1, shouldKeep));
        }
        return chapters;
    }

    /**
     * Collects top PAGE_BREAK related comments starting from current to next chapter (H1 elements)
     */
    @NotNull
    private List<Comment> collectPageBreakComments(@NotNull ChapterInfo currentChapter) {
        List<Comment> topPageBreakComments = new ArrayList<>();
        Node current = currentChapter.heading().nextSibling();

        // Traverse siblings until we reach the next h1 or end of siblings
        while (current != null && !JSoupUtils.isH1(current) && !JSoupUtils.containsH1(current)) {
            // Collect top PAGE_BREAK comments at current level
            collectPageBreakComments(current, topPageBreakComments);
            current = current.nextSibling();
        }

        return topPageBreakComments;
    }

    /**
     * Recursively collects top PAGE_BREAK related comments from an element and its descendants,
     * but only first 2 of them: <!--PAGE_BREAK--> and then either <!--PORTRAIT_ABOVE--> or <!--LANDSCAPE_ABOVE-->,
     * the rest won't be relevant as they belong to removed content.
     */
    private void collectPageBreakComments(@NotNull Node node, @NotNull List<Comment> topPageBreakComments) {
        if (topPageBreakComments.size() < 2) {
            if (node instanceof Comment comment && isPageBreakComment(comment)) {
                topPageBreakComments.add(new Comment(comment.getData()));
            } else if (node instanceof Element element) {
                for (Node child : element.childNodes()) {
                    collectPageBreakComments(child, topPageBreakComments);
                }
            }
        }
    }

    private boolean isPageBreakComment(@NotNull Comment comment) {
        String commentData = comment.getData();
        return commentData.equals(PAGE_BREAK) || commentData.equals(LANDSCAPE_ABOVE) || commentData.equals(PORTRAIT_ABOVE);
    }

    @Nullable
    private Node removeChapter(@NotNull ChapterInfo currentChapter) {
        Node current = currentChapter.heading();
        Node nextChapterNode = null; // Can return null if no next chapter found

        // Remove chapter itself and all siblings between it and next h1-tag
        while (current != null) {
            Node next = current.nextSibling();
            current.remove();

            // Check if next sibling is H1
            if (next != null && (JSoupUtils.isH1(next) || JSoupUtils.containsH1(next))) {
                nextChapterNode = next;
                break;
            }

            current = next;
        }

        return nextChapterNode;
    }

    void removePageBreakAvoids(@NotNull Document document) {
        // Polarion wraps content of a work item as it is into table's cell with table's styling "page-break-inside: avoid"
        // if it's configured to avoid page breaks:
        //
        // <table style="page-break-inside:avoid;">
        //   <tr>
        //     <td>
        //       <CONTENT>
        //     </td>
        //   </tr>
        // </table>
        //
        // This styling "page-break-inside: avoid" doesn't influence rendering by pd4ml converter,
        // but breaks rendering of tables with help of WeasyPrint. More over this configuration was initially introduced
        // for pd4ml converter because table headers are not repeated at page start when table takes more than 1 page.
        // Last drawback is not applied to WeasyPrint and thus such workaround can be safely removed.
        //
        // Taking into account that work item content can also contain tables this task should be done with cautious.
        // Removing "page-break-inside:avoid;" from table's styling doesn't help, tables are still broken. So, solution
        // is to remove that table wrapping at all. As a result above example should become just:
        //
        // <CONTENT>
        //

        Elements tables = document.select("table");
        for (Element table : tables) {
            String pageBreakInsideValue = getCssValue(table, CssProp.PAGE_BREAK_INSIDE);
            if (!pageBreakInsideValue.equals(CssProp.PAGE_BREAK_INSIDE_AVOID_VALUE)) {
                continue;
            }

            Element tbody = JSoupUtils.getSingleChildByTag(table, HtmlTag.TBODY);

            Element tr = JSoupUtils.getSingleChildByTag(tbody != null ? tbody : table, HtmlTag.TR);
            if (tr != null) {
                Element td = JSoupUtils.getSingleChildByTag(tr, HtmlTag.TD);
                if (td != null) {
                    // Move td's children to replace the table
                    for (Node contentNodes : td.childNodes()) {
                        table.before(contentNodes.clone());
                    }
                    table.remove();
                }
            }
        }
    }

    @NotNull
    @VisibleForTesting
    @SuppressWarnings({"java:S5843", "java:S5852"})
    String cutLocalUrls(@NotNull String html) {
        // This regexp consists of 2 parts combined by OR-condition. In first part it looks for <a>-tags
        // containing "/polarion/#" in its href attribute and match a content inside of this <a> into named group "content".
        // In second part of this regexp it looks for <a>-tags which href attribute starts with "http" and containing <img>-tag inside of it
        // and matches a content inside of this <a> into named group "imgContent". The sense of this regexp is to find
        // all links (as text-link or images) linking to local Polarion resources and to cut these links off, though leaving
        // their content in text.
        return RegexMatcher.get("<a[^>]+?href=[^>]*?/polarion/#[^>]*?>(?<content>[\\s\\S]+?)</a>|<a[^>]+?href=\"http[^>]+?>(?<imgContent><img[^>]+?src=\"data:[^>]+?>)</a>")
                .replace(html, regexEngine -> {
                    String content = regexEngine.group("content");
                    return content != null ? content : regexEngine.group("imgContent");
                });
    }

    /**
     * {@link CustomPageBreakPart} inserts specific 'marks' into positions where we must place page breaks.
     * The solution below replaces marks with proper html tags and does additional processing.
     */
    @NotNull
    @VisibleForTesting
    @SuppressWarnings("java:S3776")
    String processPageBrakes(@NotNull String html) {
        //remove repeated page breaks, leave just the first one
        html = RegexMatcher.get(String.format("(%s|%s){2,}", PAGE_BREAK_PORTRAIT_ABOVE, PAGE_BREAK_LANDSCAPE_ABOVE)).replace(html, regexEngine -> {
            String sequence = regexEngine.group();
            return sequence.startsWith(PAGE_BREAK_PORTRAIT_ABOVE) ? PAGE_BREAK_PORTRAIT_ABOVE : PAGE_BREAK_LANDSCAPE_ABOVE;
        });

        StringBuilder resultBuf = new StringBuilder();
        LinkedList<String> areas = new LinkedList<>(Arrays.asList(html.split(PAGE_BREAK_MARK))); //use linked list for processing list in backward order

        //the idea here is to wrap areas with different orientation into divs with correspondent class
        while (!areas.isEmpty()) {
            String area = areas.pollLast();
            String orientationClass = "portA4";
            String mark = null;
            if (area.startsWith(LANDSCAPE_ABOVE_MARK)) {
                mark = LANDSCAPE_ABOVE_MARK;
            } else if (area.startsWith(PORTRAIT_ABOVE_MARK)) {
                mark = PORTRAIT_ABOVE_MARK;
            }
            boolean firstArea = mark == null;
            area = firstArea ? area : area.substring(mark.length());

            if (!firstArea) { //see below why we don't wrap first area
                resultBuf.insert(0, DIV_END_TAG);
            }

            //here we can make additional areas processing
            area = adjustContentToFitPage(area);

            resultBuf.insert(0, area);
            if (firstArea) {
                //instead of wrapping  the first area into div we place on the body specific orientation (IMPORTANT: here we must use page identifiers but luckily in our case they are the same as the class names)
                //this will prevent from creating leading empty page
                resultBuf.insert(0, String.format("<style>body {page: %s;}</style>", orientationClass));
            } else {
                resultBuf.insert(0, String.format("<div class=\"sbb_page_break %s\">", orientationClass));
            }
        }
        return resultBuf.toString();
    }

    /**
     * When we remove some area from html we have to copy the most top page break (if it exists) in order to preserve expected orientation.
     */
    @NotNull
    private String getTopPageBrake(@NotNull String area) {
        int brakePosition = area.indexOf(PAGE_BREAK_MARK);
        if (brakePosition == -1) {
            return "";
        } else if (area.indexOf(PAGE_BREAK_LANDSCAPE_ABOVE) == brakePosition) {
            return PAGE_BREAK_LANDSCAPE_ABOVE;
        } else {
            return PAGE_BREAK_PORTRAIT_ABOVE;
        }
    }

    @NotNull
    private String filterTabularLinkedWorkitems(@NotNull String html, @NotNull List<String> selectedRoleEnumValues) {
        StringBuilder result = new StringBuilder();
        int cellStart = getLinkedWorkItemsCellStart(html, 0);
        int cellEnd = getLinkedWorkItemsCellEnd(html, cellStart);
        if (cellStart > 0 && cellEnd > 0) {
            result.append(html, 0, cellStart);
        } else {
            return html;
        }

        while (cellStart > 0 && cellEnd > 0) {
            result.append(filterByRoles(html.substring(cellStart, cellEnd), selectedRoleEnumValues));

            cellStart = getLinkedWorkItemsCellStart(html, cellEnd);
            if (cellEnd < (html.length() - 1)) {
                result.append(html, cellEnd + 1, cellStart < 0 ? html.length() : cellStart);
            }
            cellEnd = getLinkedWorkItemsCellEnd(html, cellStart);
        }

        return result.toString();
    }

    private int getLinkedWorkItemsCellStart(@NotNull String html, int prevCellEnd) {
        return html.indexOf("<td id=\"polarion_editor_field=linkedWorkItems\"", prevCellEnd);
    }

    private int getLinkedWorkItemsCellEnd(@NotNull String html, int cellStart) {
        return cellStart > 0 ? html.indexOf(TABLE_COLUMN_END_TAG, cellStart) + TABLE_COLUMN_END_TAG.length() : -1;
    }

    @NotNull
    private String filterNonTabularLinkedWorkitems(@NotNull String html, @NotNull List<String> selectedRoleEnumValues) {
        StringBuilder result = new StringBuilder();
        int spanStart = getLinkedWorkItemsSpanStart(html, 0);
        int spanEnd = getLinkedWorkItemsSpanEnd(html, spanStart);
        if (spanStart > 0 && spanEnd > 0) {
            result.append(html, 0, spanStart);
        } else {
            return html;
        }

        while (spanStart > 0 && spanEnd > 0) {
            String filtered = filterByRoles(html.substring(spanStart, spanEnd), selectedRoleEnumValues);
            result.append(filtered);

            spanStart = getLinkedWorkItemsSpanStart(html, spanEnd);
            if (spanEnd < (html.length() - 1)) {
                result.append(html, spanEnd, spanStart < 0 ? html.length() : spanStart);
            }
            spanEnd = getLinkedWorkItemsSpanEnd(html, spanStart);
        }

        return result.toString();
    }

    private int getLinkedWorkItemsSpanStart(@NotNull String html, int prevCellEnd) {
        return html.indexOf("<span id=\"polarion_editor_field=linkedWorkItems\"", prevCellEnd);
    }

    private int getLinkedWorkItemsSpanEnd(@NotNull String html, int cellStart) {
        return cellStart > 0 ? html.indexOf("&nbsp;", cellStart) : -1;
    }

    @NotNull
    @SuppressWarnings("squid:S5843")
    private String filterByRoles(@NotNull String linkedWorkItems, @NotNull List<String> selectedRoleEnumValues) {
        String polarionVersion = docxExporterPolarionService.getPolarionVersion();
        // This regexp searches for spans (named group "row") containing linked WorkItem with its role (named group "role").
        // If linked WorkItem role is not among ones selected by user we cut it from resulted HTML
        String regex = getRegexp(polarionVersion);

        StringBuilder filteredContent = new StringBuilder();

        RegexMatcher.get(regex)
                .processEntry(linkedWorkItems, regexEngine -> {
                    String role = regexEngine.group("role");
                    String row = regexEngine.group("row");
                    if (selectedRoleEnumValues.contains(role)) {
                        if (!filteredContent.isEmpty()) {
                            filteredContent.append(",<br>");
                        }
                        filteredContent.append(row);
                    }
                });
        // filteredContent - is literally content of td or span element, we need to prepend <td>/<span> with its attributes and append </td>/</span> to it
        return linkedWorkItems.substring(0, linkedWorkItems.indexOf(">") + 1)
                + filteredContent
                + linkedWorkItems.substring(linkedWorkItems.lastIndexOf("</"));
    }

    private @NotNull String getRegexp(@Nullable String polarionVersion) {
        String filterByRolesRegexBeforePolarion2404 = "(?<row><span>\\s*<span class=\"polarion-JSEnumOption\"[^>]*?>(?<role>[^<]*?)</span>:\\s*<a[^>]*?>\\s*<span[^>]*?>\\s*<img[^>]*?>\\s*<span[^>]*?>[^<]*?</span>\\s*-\\s*<span[^>]*?>[^<]*?</span>\\s*</span>\\s*</a>\\s*</span>)";
        String filterByRolesRegexAfterPolarion2404 = "(?<row><div\\s*[^>]*?>\\s*<span\\s*[^>]*?>(?<role>[^<]*?)<\\/span>\\s*<\\/div>\\s*:\\s*.*?<\\/span><\\/a><\\/span>)";

        if (polarionVersion == null) {
            return filterByRolesRegexAfterPolarion2404;
        }

        return polarionVersion.compareTo("2404") < 0 ? filterByRolesRegexBeforePolarion2404 : filterByRolesRegexAfterPolarion2404;
    }

    @NotNull
    @VisibleForTesting
    String localizeEnums(@NotNull String html, @NotNull ExportParams exportParams) {
        String localizationSettingsName = exportParams.getLocalization() != null ? exportParams.getLocalization() : NamedSettings.DEFAULT_NAME;
        final Map<String, String> localizationMap = localizationSettings.load(exportParams.getProjectId(), SettingId.fromName(localizationSettingsName)).getLocalizationMap(exportParams.getLanguage());

        //Polarion document usually keeps enumerated text values inside of spans marked with class 'polarion-JSEnumOption'.
        //Following expression retrieves such spans.
        return RegexMatcher.get("(?s)<span class=\"polarion-JSEnumOption\".+?>(?<enum>[\\w\\s]+)</span>").replace(html, regexEngine -> {
            String enumContainingSpan = regexEngine.group();
            String enumName = regexEngine.group("enum");
            String replacementString = localizationMap.get(enumName);
            return StringUtils.isEmptyTrimmed(replacementString) ? null :
                    enumContainingSpan.replace(enumName + SPAN_END_TAG, replacementString + SPAN_END_TAG);
        });
    }

    @VisibleForTesting
    void cutEmptyWIAttributes(@NotNull Document document) {
        cutEmptyWIAttributesInTables(document);
        cutEmptyWIAttributesInText(document);
    }

    private void cutEmptyWIAttributesInTables(@NotNull Document document) {
        // Iterates through <td class="polarion-dle-workitem-fields-end-table-value"> elements and if they are empty (no value) removes enclosing them tr-elements
        Elements attributeValueCells = document.select("td.polarion-dle-workitem-fields-end-table-value");
        for (Element attributeValueCell : attributeValueCells) {
            if (attributeValueCell.text().isEmpty()) {
                Element parent = attributeValueCell.parent();
                if (parent != null && parent.nodeName().equals(HtmlTag.TR)) {
                    parent.remove();
                }
            }
        }
    }

    private void cutEmptyWIAttributesInText(@NotNull Document document) {
        // Iterates through sequential spans and if second one in this sequence has title="This field is empty", removes such sequence.
        // Finally removes comma separator which and if precedes this sequence
        Elements sequentialSpans = document.select("span > span");
        for (Element span : sequentialSpans) {
            if (EMPTY_FIELD_TITLE.equals(span.attr("title"))) {
                Element parent = span.parent();
                if (parent != null) {
                    Node previousSibling = parent.previousSibling();
                    if (previousSibling instanceof TextNode previousSiblingTextNode && COMMA_SEPARATOR.equals(previousSiblingTextNode.text())) {
                        previousSiblingTextNode.remove();
                    }
                    parent.remove();
                }
            }
        }
    }

    @NotNull
    @VisibleForTesting
    @SuppressWarnings({"java:S5869", "java:S6019"})
    String properTableHeads(@NotNull String html) {
        // Searches for all subsequent table rows (<tr>-tags) inside <tbody> which contain <th>-tags
        // followed by a row which doesn't contain <th> (or closing </tbody> tag).
        // There are 2 groups in this regexp, first one is unnamed, containing <tbody> and <tr>-tags containing <th>-tags,
        // second one is named ("header") and contains those <tr>-tags which include <th>-tags. The regexp is ending
        // by positive lookahead "(?=<tr)" which doesn't take part in replacement.
        // The sense in this regexp is to find <tr>-tags containing <th>-tags and move it from <tbody> into <thead>,
        // for table headers to repeat on each page.
        return RegexMatcher.get("(<tbody>[^<]*(?<header><tr>[^<]*<th[\\s|\\S]*?))(?=(<tr|</tbody))").useJavaUtil().replace(html, regexEngine -> {
            String header = regexEngine.group("header");
            return "<thead>" + header + "</thead><tbody>";
        });
    }

    @VisibleForTesting
    void adjustCellWidth(@NotNull Document document) {
        autoCellWidth(document);

        Elements wiAttrTables = document.select("table.polarion-dle-workitem-fields-end-table");
        for (Element table : wiAttrTables) {
            table.attr(HtmlTagAttr.STYLE, "width: 100%");

            Elements attrNameCells = table.select("td.polarion-dle-workitem-fields-end-table-label");
            for (Element attrNameCell : attrNameCells) {
                attrNameCell.attr(HtmlTagAttr.STYLE, "width: 20%");
            }

            Elements attrNameValues = table.select("td.polarion-dle-workitem-fields-end-table-value");
            for (Element attrNameValue : attrNameValues) {
                attrNameValue.attr(HtmlTagAttr.STYLE, "width: 80%");
            }
        }
    }

    private void autoCellWidth(@NotNull Document document) {
        // Searches for <td> or <th> elements of regular tables whose width in styles specified not in percentage.
        // If they contain absolute values we replace them with auto, otherwise tables containing them can easily go outside boundaries of a page.
        Elements cells = document.select(String.format("%s, %s", HtmlTag.TH, HtmlTag.TD));
        for (Element cell : cells) {
            if (cell.hasAttr(HtmlTagAttr.STYLE)) {
                String style = cell.attr(HtmlTagAttr.STYLE);
                CSSStyleDeclaration cssStyle = parseCss(style);

                String widthValue = getCssValue(cssStyle, CssProp.WIDTH);
                if (!widthValue.isEmpty() && !widthValue.contains("%")) {
                    cssStyle.setProperty(CssProp.WIDTH, CssProp.WIDTH_AUTO_VALUE, null);
                    cell.attr(HtmlTagAttr.STYLE, cssStyle.getCssText());
                }
            }
        }
    }


    @NotNull
    @VisibleForTesting
    String adjustContentToFitPage(@NotNull String html) {
        html = adjustImageSizeInTables(html);
        html = adjustImageSize(html);
        return adjustTableSize(html);
    }

    @NotNull
    @VisibleForTesting
    String processComments(@NotNull String html) {
        html = RegexMatcher.get("\\[span class=comment level-(?<level>\\d+)\\]").replace(html, regexEngine -> {
            String nestingLevel = regexEngine.group("level");
            return String.format("<span class='comment level-%s'>", nestingLevel);
        });
        html = html.replace("[span class=meta]", "<span class='meta'>");
        html = html.replace("[span class=details]", "<span class='details'>");
        html = html.replace("[span class=date]", "<span class='date'>");
        html = html.replace("[span class=status-resolved]", "<span class='status-resolved'>");
        html = html.replace("[span class=author]", "<span class='author'>");
        html = html.replace("[span class=text]", "<span class='text'>");
        html = html.replace(COMMENT_END, SPAN_END_TAG);

        Document parsedHtml = Jsoup.parse(html);
        processComments(parsedHtml.body().children(), 0);

        return parsedHtml.html();
    }

    private int processComments(@NotNull Elements elements, int startingIndex) {
        int index = startingIndex;
        for (Element element : elements) {
            if (isComment(element)) {
                Element parent = element.parent();
                Element prevSibling = element.previousElementSibling();

                List<Element> elementsToInsert = transformComment(element, String.valueOf(index));
                element.remove();
                if (prevSibling != null) {
                    for (Element elementToInsert : elementsToInsert) {
                        prevSibling.after(elementToInsert);
                        prevSibling = elementToInsert;
                    }
                } else if (parent != null) {
                    parent.insertChildren(0, elementsToInsert);
                }
                index++;
            } else if (!element.children().isEmpty()) {
                index = processComments(element.children(), index);
            }
        }
        return index;
    }

    private boolean isComment(@NotNull Element element) {
        return SPAN.equals(element.nodeName()) && element.classNames().contains("comment");
    }

    private List<Element> transformComment(@NotNull Element commentElement, @NotNull String index) {
        Element commentStart = new Element(SPAN);
        commentStart.attr(CLASS, COMMENT_START_CLASS);
        commentStart.id(index);

        Element textElement = extractChildSpanByClassName(commentElement, TEXT);
        if (textElement != null) {
            commentStart.text(textElement.text());
        }

        Element metaElement = extractChildSpanByClassName(commentElement, META);
        if (metaElement != null) {
            Element authorElement = extractChildSpanByClassName(metaElement, AUTHOR);
            if (authorElement != null) {
                commentStart.attr(AUTHOR, authorElement.text());
            }
            Element dateElement = extractChildSpanByClassName(metaElement, DATE);
            if (dateElement != null) {
                commentStart.attr(DATE, dateElement.text());
            }
        }

        Element commentEnd = new Element(SPAN);
        commentEnd.attr(CLASS, COMMENT_END_CLASS);
        commentEnd.id(index);

        return List.of(commentStart, commentEnd);
    }

    private Element extractChildSpanByClassName(@NotNull Element parentElement, @NotNull String className) {
        for (Element element : parentElement.children()) {
            if (SPAN.equals(element.nodeName()) && element.classNames().contains(className)) {
                return element;
            }
        }
        return null;
    }

    @NotNull
    @VisibleForTesting
    public String cutExtraNbsp(@NotNull String html) {
        //Polarion editfield inserts a lot of extra nbsp. Also, user can copy&paste html from different sources
        //which may contain a lot of nbsp too. This may occasionally result in exceeding page width lines.
        //Seems that there is no better solution than basically remove them completely.
        return html.replaceAll("&nbsp;|\u00A0", " ");
    }

    public @NotNull String clearSelectors(@NotNull String html, @NotNull String clearSelector) {
        Document doc = Jsoup.parse(html);
        Elements elementsToRemove = doc.select(clearSelector);
        elementsToRemove.remove();
        return doc.outerHtml();
    }

    @NotNull
    String addTableOfContent(@NotNull String html) {
        final int MAX_DEFAULT_NODE_NESTING = 6;

        int startIndex = html.indexOf("<pd4ml:toc");
        RegexMatcher tocInitMatcher = RegexMatcher.get("tocInit=\"(?<startLevel>\\d+)\"");
        RegexMatcher tocMaxMatcher = RegexMatcher.get("tocMax=\"(?<maxLevel>\\d+)\"");
        while (startIndex >= 0) {
            int endIndex = html.indexOf(">", startIndex);
            String tocMacro = html.substring(startIndex, endIndex);

            int startLevel = tocInitMatcher.findFirst(tocMacro, regexEngine -> regexEngine.group("startLevel"))
                    .map(Integer::parseInt).orElse(1);

            int maxLevel = tocMaxMatcher.findFirst(tocMacro, regexEngine -> regexEngine.group("maxLevel"))
                    .map(Integer::parseInt).orElse(MAX_DEFAULT_NODE_NESTING);

            String toc = generateTableOfContent(html, startLevel, maxLevel);

            html = html.substring(0, startIndex) + toc + html.substring(endIndex + 1);


            startIndex = html.indexOf("<pd4ml:toc", endIndex);
        }

        return html;
    }

    @NotNull
    @SuppressWarnings("java:S5852") //regex checked
    private String generateTableOfContent(@NotNull String html, int startLevel, int maxLevel) {
        TocLeaf root = new TocLeaf(null, 0, null, null, null);
        AtomicReference<TocLeaf> current = new AtomicReference<>(root);

        // This regexp searches for headers of any level (elements <h1>, <h2> etc.). Level of chapter is extracted into
        // named group "level", id of <a> element inside of it (to reference from TOC) - into named group "id",
        // number of this chapter - into named group "number" and text of this header - into named group "text"
        // Also we search for wiki headers, they have slightly different structure + don't have numbers
        RegexMatcher.get("<h(?<level>[1-6])[^>]*?>[^<]*(<a id=\"(?<id>[^\"]+?)\"[^>]*?></a>[^<]*<span[^>]*>\\s*<span[^>]*>(?<number>.+?)</span>[^<]*</span>\\s*(?<text>.+?)\\s*" +
                "|<span id=\"(?<wikiHeaderId>[^\"]+?)\"[^>]*?>(?<wikiHeaderText>.+?)</span>)</h[1-6]>").processEntry(html, regexEngine -> {
            // Then we take all these named groups of certain chapter and generate appropriate element of table of content
            int level = Integer.parseInt(regexEngine.group("level"));
            String id = regexEngine.group("id");
            String number = regexEngine.group(NUMBER);
            String text = regexEngine.group("text");
            String wikiHeaderId = regexEngine.group("wikiHeaderId");
            String wikiHeaderText = regexEngine.group("wikiHeaderText");

            TocLeaf parent;
            TocLeaf newLeaf;
            if (current.get().getLevel() < level) {
                parent = current.get();
            } else {
                parent = current.get().getParent();
                while (parent.getLevel() >= level) {
                    parent = parent.getParent();
                }
            }

            newLeaf = new TocLeaf(parent, level, id != null ? id : wikiHeaderId, number, text != null ? text : wikiHeaderText);
            parent.getChildren().add(newLeaf);

            current.set(newLeaf);
        });

        return root.asString(startLevel, maxLevel);
    }

    @NotNull
    String addTableOfFigures(@NotNull final String html) {
        Map<String, String> tofByLabel = new HashMap<>();
        return RegexMatcher.get("<div data-sequence=\"(?<label>[^\"]+)\" id=\"polarion_wiki macro name=tof[^>]*></div>").replace(html, regexEngine -> {
            String label = regexEngine.group("label");
            return tofByLabel.computeIfAbsent(label, notYetGeneratedLabel -> generateTableOfFigures(html, notYetGeneratedLabel));
        });
    }

    @NotNull
    private String generateTableOfFigures(@NotNull String html, @NotNull String label) {
        StringBuilder buf = new StringBuilder(DIV_START_TAG);

        // This regexp searches for paragraphs with class 'polarion-rte-caption-paragraph'
        // with text contained in 'label' parameter in it followed by span-element with class 'polarion-rte-caption' and number inside it (number of figure),
        // which in its turn followed by a-element with name 'dlecaption_<N>' (where <N> - is figure number), which in its turn is followed by figure caption
        RegexMatcher.get(String.format("<p[^>]+?class=\"polarion-rte-caption-paragraph\"[^>]*>\\s*?.*?%s[^<]*<span data-sequence=\"%s\" " +
                "class=\"polarion-rte-caption\">(?<number>\\d+)<a name=\"dlecaption_(?<id>\\d+)\"></a></span>(?<caption>[^<]+)", label, label)).processEntry(html, regexEngine -> {
            String number = regexEngine.group(NUMBER);
            String id = regexEngine.group("id");
            String caption = regexEngine.group("caption");
            if (caption.contains(SPAN_END_TAG)) {
                caption = caption.substring(0, caption.indexOf(SPAN_END_TAG));
            }
            while (caption.contains(COMMENT_START)) {
                StringBuilder captionBuf = new StringBuilder(caption);
                int start = caption.indexOf(COMMENT_START);
                int ending = HtmlUtils.getEnding(caption, start, COMMENT_START, COMMENT_END);
                captionBuf.replace(start, ending, "");
                caption = captionBuf.toString();
            }
            buf.append(String.format("<a href=\"#dlecaption_%s\">%s %s. %s</a><br>", id, label, number, caption.trim()));
        });
        buf.append(DIV_END_TAG);
        return buf.toString();
    }

    @NotNull
    @VisibleForTesting
    String adjustReportedBy(@NotNull String html) {
        // This regexp searches for div containing 'Reported by' text and adjusts its styles
        return RegexMatcher.get("<div style=\"(?<style>[^\"]*)\">Reported by").replace(html, regexEngine -> {
            String initialStyle = regexEngine.group("style");
            String styleAdjustment = "top: 0; font-size: 8px;";
            return String.format("<div style=\"%s;%s\">Reported by", initialStyle, styleAdjustment);
        });
    }

    @NotNull
    @VisibleForTesting
    @SuppressWarnings({"java:S5852", "java:S5869"})
        //regex checked
    String cutExportToPdfButton(@NotNull String html) {
        // This regexp searches for 'Export to PDF' button enclosed into table-element with class 'polarion-TestsExecutionButton-buttons-content',
        // which in its turn enclosed into div-element with class 'polarion-TestsExecutionButton-buttons-pdf'
        return RegexMatcher.get("<div[^>]*class=\"polarion-TestsExecutionButton-buttons-pdf\">" +
                        "[\\w|\\W]*<table class=\"polarion-TestsExecutionButton-buttons-content\">[\\w|\\W]*<div[^>]*>Export to PDF</div>[\\w|\\W]*?</div>")
                .removeAll(html);
    }

    @NotNull
    @VisibleForTesting
    String adjustColumnWidthInReports(@NotNull String html) {
        // Replace fixed width value by relative one
        return html.replace("<table class=\"polarion-rp-column-layout\" style=\"width: 1000px;\">",
                "<table class=\"polarion-rp-column-layout\" style=\"width: 100%;\">");
    }

    @NotNull
    @VisibleForTesting
    String removeFloatLeftFromReports(@NotNull String html) {
        // Remove "float: left;" style definition from tables
        return RegexMatcher.get("(?<table><table[^>]*)style=\"float: left;\"")
                .replace(html, regexEngine -> regexEngine.group("table"));
    }

    @NotNull
    private static String liftHeadingTag(@NotNull String tag) {
        return switch (tag) {
            case "h2" -> "h1";
            case "h3" -> "h2";
            case "h4" -> "h3";
            case "h5" -> "h4";
            case "h6" -> "h5";
            default -> "h6";
        };
    }

    @NotNull
    @SuppressWarnings({"java:S5852", "java:S5857"}) //need by design
    private String adjustImageAlignmentForPDF(@NotNull String html) {
        String startImgPattern = "<img [^>]*style=\"([^>]*)\".*?>";
        IRegexEngine regexEngine = RegexMatcher.get(startImgPattern).createEngine(html);
        StringBuilder sb = new StringBuilder();

        while (true) {
            String group;
            CSSStyle css;
            CSSStyle.Rule displayRule;
            do {
                do {
                    if (!regexEngine.find()) {
                        regexEngine.appendTail(sb);
                        return sb.toString();
                    }

                    group = regexEngine.group();
                    String style = regexEngine.group(1);
                    css = CSSStyle.parse(style);
                    displayRule = css.getRule("display");
                } while (displayRule == null);
            } while (!"block".equals(displayRule.getValue()));

            final String align;
            CSSStyle.Rule marginRule = css.getRule("margin");
            if (marginRule != null && "auto 0px auto auto".equals(marginRule.getValue())) {
                align = "right";
            } else {
                align = "center";
            }

            group = "<div style=\"text-align: " + align + "\">" + group + DIV_END_TAG;
            regexEngine.appendReplacement(sb, group);
        }
    }

    @NotNull
    @VisibleForTesting
    @SuppressWarnings("java:S5852") //regex checked
    public String adjustImageSize(@NotNull String html) {
        // We are looking here for images which widths and heights are explicitly specified.
        // Named group "prepend" - is everything which stands before width/height and named group "append" - after.
        // Then we check if width (named group "width") exceeds limit we override it by value "100%"
        return RegexMatcher.get("(<img(?<prepend>[^>]+?)width:\\s*?(?<width>[\\d.]*?)(?<measureWidth>px|ex);\\s*?height:\\s*?(?<height>[\\d.]*?)(?<measureHeight>px|ex)(?<append>[^>]*?)>)")
                .replace(html, regexEngine -> {
                    float width = parseDimension(regexEngine, WIDTH, MEASURE_WIDTH);
                    float height = parseDimension(regexEngine, HEIGHT, MEASURE_HEIGHT);

                    String prepend = regexEngine.group("prepend");
                    String append = regexEngine.group("append");

                    return generateAdjustedImageTag(prepend, append, width, height, A4_PORTRAIT_WIDTH, A4_PORTRAIT_HEIGHT);
                });
    }

    private float parseDimension(IRegexEngine regexEngine, String dimension, String measure) {
        float value = Float.parseFloat(regexEngine.group(dimension));
        if (MEASURE_EX.equals(regexEngine.group(measure))) {
            value *= EX_TO_PX_RATIO;
        }
        return value;
    }

    private String generateAdjustedImageTag(String prepend, String append, float width, float height, float maxWidth, float maxHeight) {
        float widthExceedingRatio = width / maxWidth;
        float heightExceedingRatio = height / maxHeight;

        if (widthExceedingRatio <= 1 && heightExceedingRatio <= 1) {
            return null;
        }

        final float adjustedWidth;
        final float adjustedHeight;

        if (widthExceedingRatio > heightExceedingRatio) {
            adjustedWidth = width / widthExceedingRatio;
            adjustedHeight = height / widthExceedingRatio;
        } else {
            adjustedWidth = width / heightExceedingRatio;
            adjustedHeight = height / heightExceedingRatio;
        }

        return "<img" + prepend + "width: " + ((int) adjustedWidth) + "px; height: " + ((int) adjustedHeight) + "px" + append + ">";
    }

    @NotNull
    @VisibleForTesting
    public String adjustTableSize(@NotNull String html) {
        // We are looking here for tables which widths are explicitly specified.
        // When width exceeds limit we override it by value "100%"
        return RegexMatcher.get("<table[^>]+?width:\\s*?(?<width>[\\d.]+?)(?<measure>px|%)").replace(html, regexEngine -> {
            String width = regexEngine.group(WIDTH);
            String measure = regexEngine.group(MEASURE);
            float widthParsed = Float.parseFloat(width);
            if (MEASURE_PX.equals(measure) && widthParsed > A4_PORTRAIT_WIDTH || MEASURE_PERCENT.equals(measure) && widthParsed > FULL_WIDTH_PERCENT) {
                return regexEngine.group().replace(width + measure, "100%");
            } else {
                return null;
            }
        });
    }

    @NotNull
    @VisibleForTesting
    @SuppressWarnings({"java:S3776", "java:S5852", "java:S5857", "java:S135"}) //regex checked
    public String adjustImageSizeInTables(@NotNull String html) {
        StringBuilder buf = new StringBuilder();
        int pos = 0;

        //The main idea below is:
        // 1) find the most top-level tables
        // 2) replace all suspicious img tags inside tables with reduced width

        while (true) {
            int tableStart = html.indexOf(TABLE_OPEN_TAG, pos);
            if (tableStart == -1) {
                buf.append(html.substring(pos));
                break;
            }
            int tableEnd = findTableEnd(html, tableStart);
            if (tableEnd == -1) {
                buf.append(html.substring(pos));
                break;
            } else {
                tableEnd = tableEnd + TABLE_END_TAG.length();
            }
            if (pos != tableStart) {
                buf.append(html, pos, tableStart);
            }
            String tableHtml = html.substring(tableStart, tableEnd);

            String modifiedTableContent = RegexMatcher.get("(<img[^>]+?width:\\s*?(?<widthValue>(?<width>[\\d.]*?)(?<measure>px|ex)|auto);[^>]+?>)").replace(tableHtml, regexEngine -> {
                String widthValue = regexEngine.group("widthValue");
                float width;
                if (widthValue.equals("auto")) {
                    width = Float.MAX_VALUE;
                } else {
                    width = Float.parseFloat(regexEngine.group(WIDTH));
                    if (MEASURE_EX.equals(regexEngine.group(MEASURE))) {
                        width = width * EX_TO_PX_RATIO;
                    }
                }
                float columnCountBasedWidth = getImageWidthBasedOnColumnsCount(tableHtml, regexEngine.group());
                float paramsBasedWidth = A4_PORTRAIT_WIDTH / 3f;
                float maxWidth = columnCountBasedWidth != -1 && columnCountBasedWidth < paramsBasedWidth ? columnCountBasedWidth : paramsBasedWidth;
                return width <= maxWidth ? null : regexEngine.group()
                        .replaceAll("max-width:\\s*?([\\d.]*?(px|ex)|auto);", "") //it seems that max-width doesn't work in WP
                        .replaceAll("width:\\s*?([\\d.]*?(px|ex)|auto);", "")     //remove width too, we will add it later
                        .replaceAll("height:\\s*?[\\d.]*?(px|ex);", "")           //remove height completely in order to keep image ratio
                        .replace("style=\"", "style=\"width: " + ((int) maxWidth) + "px;");
            });

            buf.append(modifiedTableContent);
            pos = tableEnd;
        }
        return buf.toString();
    }

    @SuppressWarnings("java:S135")
    private int findTableEnd(String html, int tableStart) {
        int pos = tableStart;
        int tableEnd = -1;
        int depth = 0;
        while (pos < html.length()) {
            int nextTableStart = html.indexOf(TABLE_OPEN_TAG, pos);
            int nextTableEnd = html.indexOf(TABLE_END_TAG, pos);
            if (nextTableStart != -1 && nextTableStart < nextTableEnd) {
                depth++;
                pos = nextTableStart + TABLE_OPEN_TAG.length();
            } else if (nextTableEnd != -1) {
                depth--;
                pos = nextTableEnd + TABLE_END_TAG.length();
                if (depth == 0) {
                    tableEnd = nextTableEnd;
                    break;
                }
            } else {
                break;
            }
        }
        return tableEnd;
    }

    @VisibleForTesting
    int getImageWidthBasedOnColumnsCount(String table, String imgTag) {
        int imgPosition = table.indexOf(imgTag);
        int trStartPosition = table.substring(0, imgPosition).lastIndexOf(TABLE_ROW_OPEN_TAG);
        int trEndPosition = table.indexOf(TABLE_ROW_END_TAG, imgPosition);
        if (trStartPosition != -1 && trEndPosition != -1) {
            int columnsCount = columnsCount(table.substring(trStartPosition, trEndPosition));
            if (columnsCount > 0) {
                return A4_PORTRAIT_WIDTH / columnsCount;
            }
        }
        return -1;
    }

    @VisibleForTesting
    int columnsCount(String string) {
        return (string.length() - string.replace(TABLE_COLUMN_OPEN_TAG, "").length()) / TABLE_COLUMN_OPEN_TAG.length();
    }

    @SneakyThrows
    @SuppressWarnings({"java:S5852", "java:S5857"}) //need by design
    public String replaceResourcesAsBase64Encoded(String html) {
        return MediaUtils.inlineBase64Resources(html, fileResourceProvider);
    }

    public String internalizeLinks(String html) {
        return httpLinksHelper.internalizeLinks(html);
    }

    public String replaceLinks(String html) {
        String baseUrl = getBaseUrl();
        // Use RegexMatcher to process anchor tags with href
        return RegexMatcher.get("<a\\s+[^>]*href=\\\"(?<href>[^\\\"]*)\\\"[^>]*>")
            .replace(html, regexEngine -> {
                String originalHref = regexEngine.group("href");
                String anchorTag = regexEngine.group();
                if (originalHref.matches("#dlecaption_\\d+") || isInTOC(anchorTag)) {
                    return anchorTag;
                }
                if (isRelativeLink(originalHref)) {
                    String absoluteUrl = resolveUrl(baseUrl, originalHref);
                    return anchorTag.replace("href=\"" + originalHref + "\"", "href=\"" + absoluteUrl + "\"");
                }
                return anchorTag;
            });
    }

    @VisibleForTesting
    boolean isInTOC(String anchorTag) {
        return anchorTag.contains("class=\"toc\"") || anchorTag.contains("class='toc'");
    }

    @VisibleForTesting
    boolean isRelativeLink(String url) {
        return !(url.startsWith(HTTP_PROTOCOL_PREFIX) || url.startsWith(HTTPS_PROTOCOL_PREFIX) || url.startsWith("#") || url.startsWith("mailto:"));
    }

    @VisibleForTesting
    String resolveUrl(String baseUrl, String relativeUrl) {
        try {
            URI base = new URI(baseUrl);
            URI resolved = base.resolve(relativeUrl);
            return resolved.toString();
        } catch (URISyntaxException e) {
            return relativeUrl;
        }
    }

    @VisibleForTesting
    String getBaseUrl() {
        String polarionBaseUrl = System.getProperty(PolarionProperties.BASE_URL, LOCALHOST);
        if (!polarionBaseUrl.contains(LOCALHOST)) {
            return enrichByProtocolPrefix(polarionBaseUrl);
        }
        String hostname = Configuration.getInstance().cluster().nodeHostname();
        return enrichByProtocolPrefix(hostname);
    }

    @VisibleForTesting
    String enrichByProtocolPrefix(String hostname) {
        if (com.polarion.core.util.StringUtils.isEmpty(hostname) || hostname.startsWith(HTTP_PROTOCOL_PREFIX) || hostname.startsWith(HTTPS_PROTOCOL_PREFIX)) {
            return hostname;
        } else {
            return HTTP_PROTOCOL_PREFIX + hostname;
        }
    }

    private boolean hasCustomPageBreaks(String html) {
        return html.contains(PAGE_BREAK_MARK);
    }

    private String getCssValue(@NotNull Element element, @NotNull String cssProperty) {
        CSSStyleDeclaration cssStyle = getCssStyle(element);
        return getCssValue(cssStyle, cssProperty);
    }

    private String getCssValue(@NotNull CSSStyleDeclaration cssStyle, @NotNull String cssProperty) {
        return Optional.ofNullable(cssStyle.getPropertyValue(cssProperty)).orElse("").trim();
    }

    private CSSStyleDeclaration getCssStyle(@NotNull Element element) {
        String style = "";
        if (element.hasAttr(HtmlTagAttr.STYLE)) {
            style = element.attr(HtmlTagAttr.STYLE);
        }
        return parseCss(style);
    }

    private CSSStyleDeclaration parseCss(@NotNull String style) {
        return CssUtils.parseCss(parser, style);
    }

    /**
     * Internal record to hold chapter information during processing.
     */
    private record ChapterInfo(@NotNull Element heading, boolean shouldKeep) {
    }
}
