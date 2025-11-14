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
import ch.sbb.polarion.extension.docx_exporter.settings.LocalizationSettings;
import ch.sbb.polarion.extension.docx_exporter.util.html.HtmlLinksHelper;
import com.polarion.alm.shared.util.StringUtils;
import com.polarion.core.boot.PolarionProperties;
import com.polarion.core.config.Configuration;
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
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

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
    private static final String SPAN = "span";
    private static final String SPAN_END_TAG = "</span>";
    private static final String COMMENT_START = "[span";
    private static final String COMMENT_END = "[/span]";
    private static final String WIDTH = "width";
    private static final String MEASURE = "measure";
    private static final String MEASURE_WIDTH = "measureWidth";
    private static final String HEIGHT = "height";
    private static final String MEASURE_HEIGHT = "measureHeight";
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
    private static final String URL_PROJECT_ID_PREFIX = "/polarion/#/project/";
    private static final String URL_WORK_ITEM_ID_PREFIX = "workitem?id=";
    private static final String POLARION_URL_MARKER = "/polarion/#";
    private static final String ROWSPAN_ATTR = "rowspan";
    private static final String RIGHT_ALIGNMENT_MARGIN = "auto 0px auto auto";
    private static final String TABLE_OF_FIGURES_ANCHOR_ID_PREFIX = "dlecaption_";

    private static final String LOCALHOST = "localhost";
    public static final String HTTP_PROTOCOL_PREFIX = "http://";
    public static final String HTTPS_PROTOCOL_PREFIX = "https://";

    private final FileResourceProvider fileResourceProvider;
    private final LocalizationSettings localizationSettings;
    private final HtmlLinksHelper httpLinksHelper;
    private final @NotNull CSSOMParser parser = new CSSOMParser();

    public HtmlProcessor(FileResourceProvider fileResourceProvider, LocalizationSettings localizationSettings, HtmlLinksHelper httpLinksHelper) {
        this.fileResourceProvider = fileResourceProvider;
        this.localizationSettings = localizationSettings;
        this.httpLinksHelper = httpLinksHelper;
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

        // Moves WorkItem content out of table wrapping it
        removePageBreakAvoids(document);

        // Fixes nested HTML lists structure
        fixNestedLists(document);

        // Localize enumeration values
        localizeEnums(document, exportParams);

        // Polarion doesn't place table rows with th-tags into thead, placing them in table's tbody, which is wrong as table header won't
        // repeat on each next page if table is split across multiple pages. We are fixing this moving such rows into thead.
        fixTableHeads(document);

        // If on next step we placed into thead rows which contain rowspan > 1 and this "covers" rows which are still in tbody, we are fixing
        // this here, moving such rows also in thead
        fixTableHeadRowspan(document);

        // Images with styles and "display: block" are searched here. For such images we do following: wrap them into div with text-align style
        // and value "right" if image margin is "auto 0px auto auto" or "center" otherwise.
        adjustImageAlignment(document);

        // Adjusts WorkItem attributes tables to stretch to full page width for better usage of page space and better readability.
        // Also changes absolute widths of normal table cells from absolute values to "auto" if "Fit tables and images to page" is on
        adjustCellWidth(document);

        // ----
        // This sequence is important! We need first filter out Linked WorkItems and only then cut empty attributes,
        // cause after filtering Linked WorkItems can become empty. Also cutting local URLs should happen afterwards
        // as filtering workitems relies among other on anchors.
        if (!selectedRoleEnumValues.isEmpty()) {
            filterTabularLinkedWorkItems(document, selectedRoleEnumValues);
            filterNonTabularLinkedWorkItems(document, selectedRoleEnumValues);
        }
        if (exportParams.isCutEmptyWIAttributes()) {
            cutEmptyWIAttributes(document);
        }
        rewritePolarionUrls(document); // Rewrites Polarion Work Item hyperlinks so that they become intra-document anchor links. Should precede cutting local URLs.
        if (exportParams.isCutLocalUrls()) {
            cutLocalUrls(document);
        }
        // ----

        new LiveDocTOCGenerator().addTableOfContent(document);
        addTableOfFigures(document);

        html = document.body().html();

        // III. THIRD SECTION - and finally again back to manipulating HTML as a String.
        // ----------------

        // Jsoup may convert &dollar; back to $ in some cases, so we need to replace it again
        html = encodeDollarSigns(html);

        html = replaceResourcesAsBase64Encoded(html);
        html = MediaUtils.removeSvgUnsupportedFeatureHint(html); //note that there is one more replacement attempt before replacing images with base64 representation

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
                int newLevel = Math.max(1, Math.min(6, level - 1));
                heading.tagName("h" + newLevel);
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
        // but breaks rendering of tables with help of WeasyPrint. Moreover, this configuration was initially introduced
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

    public void fixNestedLists(Document doc) {
        // Polarion generates not valid HTML for multi-level lists:
        //
        // <ol>
        //   <li>first item</li>
        //   <ol>
        //     <li>sub-item</li
        //   </ol>
        // </ol>
        //
        // By HTML specification ol/ul elements can contain only li-elements as their direct children.
        // So, valid HTML will be:
        //
        // <ol>
        //   <li>first item
        //     <ol>
        //       <li>sub-item</li
        //     </ol>
        //   </li>
        // </ol>
        //
        // This method fixes the problem described above.

        boolean modified;
        String listsSelector = String.format("%s, %s", HtmlTag.OL, HtmlTag.UL);
        do {
            modified = false;
            Elements lists = doc.select(listsSelector);

            for (Element list : lists) {
                modified = fixNestedLists(list);
                if (modified) {
                    break; // Restart to avoid concurrent modification
                }
            }
        } while (modified); // Repeat to cover all nesting levels, until the point when nothing was modified / fixed
    }

    private boolean fixNestedLists(@NotNull Element list) {
        for (Element child : list.children()) {
            if (child.tagName().equals(HtmlTag.OL) || child.tagName().equals(HtmlTag.UL)) {
                Element previousSibling = child.previousElementSibling();
                if (previousSibling != null && previousSibling.tagName().equals(HtmlTag.LI)) {
                    child.remove();
                    previousSibling.appendChild(child);
                    return true;
                }
            }
        }
        return false;
    }

    @VisibleForTesting
    void localizeEnums(@NotNull Document document, @NotNull ExportParams exportParams) {
        String localizationSettingsName = exportParams.getLocalization() != null ? exportParams.getLocalization() : NamedSettings.DEFAULT_NAME;
        Map<String, String> localizationMap = localizationSettings.load(exportParams.getProjectId(), SettingId.fromName(localizationSettingsName)).getLocalizationMap(exportParams.getLanguage());

        Elements enums = document.select("span.polarion-JSEnumOption");
        for (Element enumElement : enums) {
            String replacementString = localizationMap.get(enumElement.text());
            if (!StringUtils.isEmptyTrimmed(replacementString)) {
                enumElement.text(replacementString);
            }
        }
    }

    @VisibleForTesting
    void fixTableHeads(@NotNull Document document) {
        Elements tables = document.select(HtmlTag.TABLE);
        for (Element table : tables) {
            List<Element> headerRows = JSoupUtils.getRowsWithHeaders(table);
            if (headerRows.isEmpty()) {
                continue;
            }

            Element header = table.selectFirst(HtmlTag.THEAD);
            if (header == null) {
                header = new Element(HtmlTag.THEAD);
                table.prependChild(header);
            }

            for (Element headerRow : headerRows) {
                // Parent of each header row can't be null as we got them as child nodes of a table
                if (!Objects.requireNonNull(headerRow.parent()).tagName().equals(HtmlTag.THEAD)) {
                    // Header row is located not in thead - moving it there
                    headerRow.remove();
                    header.appendChild(headerRow);
                }
            }
        }
    }

    /**
     * Fixes malformed tables where thead contains single one row which cells has rowspan attribute greater than 1.
     * Such cells semantically extend beyond the thead boundary, which causes incorrect table rendering.
     * This method extends thead by moving rows from tbody into thead to match the rowspan values.
     */
    @VisibleForTesting
    public void fixTableHeadRowspan(@NotNull Document document) {
        Elements tables = document.select(HtmlTag.TABLE);

        for (Element table : tables) {
            Element thead = table.selectFirst(HtmlTag.THEAD);
            if (thead != null) {
                Element headRow = getHeadRow(thead);
                if (headRow != null) {
                    int maxRowspan = getMaxRowspan(headRow);
                    // If all cells have rowspan=1 or no rowspan, nothing to fix
                    if (maxRowspan <= 1) {
                        continue;
                    }

                    List<Element> tbodyRows = JSoupUtils.getBodyRows(table);

                    // Move (maxRowspan - 1) rows from tbody to thead
                    int rowsToMove = Math.min(maxRowspan - 1, tbodyRows.size());
                    for (int i = 0; i < rowsToMove; i++) {
                        Element rowToMove = tbodyRows.get(i);
                        rowToMove.remove();
                        thead.appendChild(rowToMove);
                    }
                }
            }
        }
    }

    private Element getHeadRow(@NotNull Element thead) {
        Elements theadRows = thead.select("> tr");
        if (theadRows.size() != 1) {
            return null;
        }
        return theadRows.first();
    }

    private int getMaxRowspan(@NotNull Element headRow) {
        Elements cells = headRow.select("> th, > td");
        int maxRowspan = 1;

        // Find the maximum rowspan value
        for (Element cell : cells) {
            if (cell.hasAttr(ROWSPAN_ATTR)) {
                try {
                    int rowspan = Integer.parseInt(cell.attr(ROWSPAN_ATTR));
                    if (rowspan > maxRowspan) {
                        maxRowspan = rowspan;
                    }
                } catch (NumberFormatException e) {
                    // Ignore invalid rowspan values
                }
            }
        }
        return maxRowspan;
    }

    @VisibleForTesting
    void cutLocalUrls(@NotNull Document document) {
        // Looks for <a>-tags containing "/polarion/#" in its href attribute or for <a>-tags which href attribute starts with "http" and containing <img>-tag inside of it.
        // Then it moves content of such links outside it and removing links themselves.
        for (Element link : document.select("a[href]")) {
            String href = link.attr(HtmlTagAttr.HREF);
            boolean cutUrl = href.contains(POLARION_URL_MARKER) || JSoupUtils.isImg(link.firstElementChild());
            if (cutUrl) {
                for (Node contentNodes : link.childNodes()) {
                    link.before(contentNodes.clone());
                }
                link.remove();
            }
        }
    }

    @VisibleForTesting
    void rewritePolarionUrls(@NotNull Document document) {
        Set<String> workItemAnchors = new HashSet<>();
        for (Element anchor : document.select("a[id^=work-item-anchor-]")) {
            workItemAnchors.add(anchor.id());
        }

        for (Element link : document.select("a[href]")) {
            String href = link.attr(HtmlTagAttr.HREF);

            String afterProject = substringAfter(href, URL_PROJECT_ID_PREFIX);
            String projectId = substringBefore(afterProject, "/", false);
            String workItemId = substringAfter(afterProject, URL_WORK_ITEM_ID_PREFIX);

            if (afterProject == null || projectId == null || workItemId == null) {
                continue;
            }

            workItemId = substringBefore(workItemId, "&", true);
            workItemId = workItemId != null ? substringBefore(workItemId, "#", true) : null;
            if (!StringUtils.isEmpty(workItemId)) {
                String expectedAnchorId = "work-item-anchor-" + projectId + "/" + workItemId;
                if (workItemAnchors.contains(expectedAnchorId)) {
                    link.attr(HtmlTagAttr.HREF, "#" + expectedAnchorId);
                }
            }
        }
    }

    @Nullable
    private String substringBefore(@Nullable String str, @NotNull String marker, boolean initialStringIfNotFound) {
        if (str != null && str.contains(marker)) {
            return str.substring(0, str.indexOf(marker));
        } else {
            return initialStringIfNotFound ? str : null;
        }
    }

    @Nullable
    private String substringAfter(@Nullable String str, @NotNull String marker) {
        return str != null && str.contains(marker) ? str.substring(str.indexOf(marker) + marker.length()) : null;
    }

    private void filterTabularLinkedWorkItems(@NotNull Document document, @NotNull List<String> selectedRoleEnumValues) {
        Elements linkedWorkItemsCells = document.select("td[id='polarion_editor_field=linkedWorkItems']");
        for (Element linkedWorkItemsCell : linkedWorkItemsCells) {
            filterByRoles(linkedWorkItemsCell, selectedRoleEnumValues);
        }
    }

    @VisibleForTesting
    void filterNonTabularLinkedWorkItems(@NotNull Document document, @NotNull List<String> selectedRoleEnumValues) {
        Elements linkedWorkItemsContainers = document.select("span[id='polarion_editor_field=linkedWorkItems']");
        for (Element linkedWorkItemsContainer : linkedWorkItemsContainers) {
            filterByRoles(linkedWorkItemsContainer, selectedRoleEnumValues);
        }
    }

    private void filterByRoles(@NotNull Element linkedWorkItemsContainer, @NotNull List<String> selectedRoleEnumValues) {
        Element nextChild = linkedWorkItemsContainer.firstElementChild();

        List<LinkedWorkitemNodes> linkedWorkitemNodesList = new LinkedList<>();
        while (nextChild != null) {
            LinkedWorkitemNodes linkedWorkitemNodes = extractLinkedWorkItemNodes(nextChild);
            if (linkedWorkitemNodes != null) {
                nextChild = linkedWorkitemNodes.getNextSibling();
                if (!selectedRoleEnumValues.contains(linkedWorkitemNodes.role)) {
                    linkedWorkitemNodes.removeAll();
                } else {
                    linkedWorkitemNodesList.add(linkedWorkitemNodes);
                }
            } else {
                nextChild = null;
            }
        }

        for (int i = 0; i < linkedWorkitemNodesList.size(); i++) {
            if (i < linkedWorkitemNodesList.size() - 1) {
                linkedWorkitemNodesList.get(i).appendComma(); // Separate each group by comma
            } else {
                linkedWorkitemNodesList.get(i).removeBr(); // Remove br-tag after last group
            }
        }
    }

    private LinkedWorkitemNodes extractLinkedWorkItemNodes(@NotNull Element nextChild) {
        Element roleElement = null;
        String role = null;
        if (nextChild.tagName().equals(HtmlTag.DIV)) {
            Element internalElement = nextChild.children().size() == 1 ? nextChild.firstElementChild() : null;
            if (internalElement != null && internalElement.tagName().equals(HtmlTag.SPAN) && !internalElement.text().isBlank()) {
                roleElement = nextChild;
                role = internalElement.text();
            }
        }
        if (roleElement == null) {
            return null; // Not expected elements structure, stop processing
        }

        TextNode colonNode = extractColonNode(roleElement.nextSibling());
        if (colonNode == null) {
            return null; // Not expected elements structure, stop processing
        }

        Element linkedWorkItemElement = extractLinkedWorkItemElement(colonNode.nextSibling());
        if (linkedWorkItemElement == null) {
            return null; // Not expected elements structure, stop processing
        }

        // There will be no br-tag after last linked WorkItem, so not obligatory
        Element brElement = extractBrElement(linkedWorkItemElement.nextElementSibling());

        return new LinkedWorkitemNodes(role, roleElement, colonNode, linkedWorkItemElement, brElement);
    }

    private TextNode extractColonNode(@Nullable Node node) {
        if (node instanceof TextNode textNode && textNode.text().contains(":")) {
            return textNode;
        } else {
            return null;
        }
    }

    private Element extractLinkedWorkItemElement(@Nullable Node node) {
        if (node instanceof Element element) {
            return element.select("> a.polarion-Hyperlink").isEmpty() ? null : element;
        } else {
            return null;
        }
    }

    private Element extractBrElement(@Nullable Element element) {
        return element != null && element.tagName().equals(HtmlTag.BR) ? element : null;
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

    @VisibleForTesting
    void adjustImageAlignment(@NotNull Document document) {
        Elements images = document.select(HtmlTag.IMG);
        for (Element image : images) {
            if (image.hasAttr(HtmlTagAttr.STYLE)) {
                String style = image.attr(HtmlTagAttr.STYLE);
                CSSStyleDeclaration cssStyle = parseCss(style);

                String displayValue = getCssValue(cssStyle, CssProp.DISPLAY);
                if (!CssProp.DISPLAY_BLOCK_VALUE.equals(displayValue)) {
                    continue;
                }

                Element wrapper = new Element(HtmlTag.DIV);

                String marginValue = Optional.ofNullable(cssStyle.getPropertyValue(CssProp.MARGIN)).orElse("").trim();
                if (RIGHT_ALIGNMENT_MARGIN.equals(marginValue)) {
                    wrapper.attr(HtmlTagAttr.STYLE, String.format("%s: %s;", CssProp.TEXT_ALIGN, CssProp.TEXT_ALIGN_RIGHT_VALUE));
                } else {
                    wrapper.attr(HtmlTagAttr.STYLE, String.format("%s: %s;", CssProp.TEXT_ALIGN, CssProp.TEXT_ALIGN_CENTER_VALUE));
                }

                Element previousSibling = image.previousElementSibling();
                if (previousSibling != null) {
                    previousSibling.after(wrapper);
                } else {
                    Element parent = image.parent();
                    Objects.requireNonNullElse(parent, document.body()).prependChild(wrapper);
                }
                image.remove();
                wrapper.appendChild(image);
            }
        }
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

    @VisibleForTesting
    void addTableOfFigures(@NotNull Document document) {
        for (Element tofPlaceholder : document.select("div[id*=macro name=tof][data-sequence]")) {
            String label = tofPlaceholder.dataset().get("sequence");
            Element tof = generateTableOfFigures(document, label);
            tofPlaceholder.before(tof);
            tofPlaceholder.remove();
        }
    }

    private Element generateTableOfFigures(@NotNull Document document, @NotNull String label) {
        Element tof = new Element(HtmlTag.DIV);
        for (Element captionAnchor : document.select(String.format("p.polarion-rte-caption-paragraph span.polarion-rte-caption[data-sequence=%s] a[name^=%s]",
                escapeCssSelectorValue(label), TABLE_OF_FIGURES_ANCHOR_ID_PREFIX))) {
            // CSS selector above guarantees that parent below won't be null
            Element captionNumber = Objects.requireNonNull(captionAnchor.parent());

            // CSS selector above guarantees that 'name' attribute of anchor below won't be null and will have length at least of TABLE_OF_FIGURES_ANCHOR_ID_PREFIX constant length
            String anchorId = captionAnchor.attr("name").substring(TABLE_OF_FIGURES_ANCHOR_ID_PREFIX.length());
            Node numberNode = captionNumber.childNodes().stream().filter(TextNode.class::isInstance).findFirst().orElse(null);
            String number = numberNode instanceof TextNode numberTextNode ? numberTextNode.text() : null;
            Node captionNode = captionNumber.nextSibling();
            String caption = captionNode instanceof TextNode captionTextNode ? captionTextNode.text() : null;

            if (StringUtils.isEmpty(anchorId) || number == null || caption == null) {
                continue;
            }

            while (caption.contains(COMMENT_START)) {
                StringBuilder captionBuf = new StringBuilder(caption);
                int start = caption.indexOf(COMMENT_START);
                int ending = HtmlUtils.getEnding(caption, start, COMMENT_START, COMMENT_END);
                captionBuf.replace(start, ending, "");
                caption = captionBuf.toString();
            }

            Element tofItem = new Element(HtmlTag.A);
            tofItem.attr(HtmlTagAttr.HREF, String.format("#dlecaption_%s", anchorId));
            tofItem.text(String.format("%s %s. %s", label, number, caption.trim()));

            tof.appendChild(tofItem);
            tof.appendChild(new Element(HtmlTag.BR));
        }

        return tof;
    }

    private String escapeCssSelectorValue(String value) {
        if (value == null) {
            return "";
        }

        return "'"
                + value
                .replace("\\", "\\\\") // Escape backslash (must be first!)
                .replace("\"", "\\\"") // Escape double quotes
                .replace("'", "\\'")   // Escape single quotes
                + "'";
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

    private record LinkedWorkitemNodes(@NotNull String role, @NotNull Element roleElement, @NotNull TextNode colonNode,
                                       @NotNull Element linkedWorkItemElement, @Nullable Element brElement) {
        Element getNextSibling() {
            return brElement != null ? brElement.nextElementSibling() : linkedWorkItemElement.nextElementSibling();
        }

        void removeAll() {
            roleElement.remove();
            colonNode.remove();
            linkedWorkItemElement.remove();
            if (brElement != null) {
                brElement.remove();
            }
        }

        void removeBr() {
            if (brElement != null) {
                brElement.remove();
            }
        }

        void appendComma() {
            linkedWorkItemElement.after(",");
        }

    }
}
