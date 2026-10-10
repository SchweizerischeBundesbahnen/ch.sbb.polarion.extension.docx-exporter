package ch.sbb.polarion.extension.docx_exporter.pandoc.performance;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Documents of the shapes which make an export slow, each timed against a reference time of the exporter and of pandoc.
 * <p>
 * The reference times of each part are in {@code performance/reference-times-<architecture>.properties}.
 * </p>
 */
class ExportPerformanceTest extends BasePerformanceTest {

    /**
     * A small document, which takes the exporter little but what every export costs: a cost added to every export shows
     * here as a multiple of its time. It is judged against the fixed piece of JDK work, the other documents against it.
     */
    @Test
    void exportsASmallDocument() {
        Timing timing = export(PerformanceRun.SMALL_DOCUMENT, SMALL_DOCUMENT_TITLE, smallDocument(), portraitA4().build());

        assertThat(count(timing.docx(), "p")).isGreaterThan(10);
        assertWithinReference(timing);
    }

    @Test
    void exportsALargeTable() {
        Timing timing = export("largeTable", "A large table", Documents.largeTable(400), portraitA4().build());

        assertThat(count(timing.docx(), "tr")).isGreaterThan(400);
        assertWithinReference(timing);
    }

    @Test
    void exportsCellsRunningAcrossPages() {
        Timing timing = export("longCells", "Long cells", Documents.longCells(3, 10_000), portraitA4().build());

        assertThat(count(timing.docx(), "tc")).isEqualTo(3);
        assertWithinReference(timing);
    }

    @Test
    void exportsManyImages() {
        Timing timing = export("manyImages", "Many images", Documents.manyImages(80), portraitA4().build());

        assertThat(count(timing.docx(), "drawing")).isEqualTo(80);
        assertWithinReference(timing);
    }

    @Test
    void exportsManyWorkItems() {
        Timing timing = export("manyWorkItems", "Many work items", Documents.manyWorkItems(300), portraitA4().build());

        assertThat(count(timing.docx(), "tbl")).isGreaterThanOrEqualTo(300);
        assertWithinReference(timing);
    }

    @Test
    void exportsSectionsWhichPageBreaksTurn() {
        Timing timing = export("pageBreakSections", "Page breaks", Documents.pageBreakSections(40), portraitA4().build());

        assertThat(count(timing.docx(), "sectPr")).isGreaterThanOrEqualTo(40);
        assertWithinReference(timing);
    }

    @Test
    void exportsCrampedTables() {
        Timing timing = export("crampedTables", "Cramped tables", Documents.crampedTables(30), portraitA4().build());

        assertThat(count(timing.docx(), "tbl")).isEqualTo(30);
        assertWithinReference(timing);
    }

    /**
     * A large document of text, tables, photographs and diagrams: what every export costs is a small part of it, and a cost
     * which grows with the document shows. pandoc takes gigabytes of memory for a document of tens of MB
     * (SchweizerischeBundesbahnen/pandoc-service#271), so it stays at some 10 MB.
     */
    @Test
    void exportsALargeDocumentWithImagesAndDiagrams() {
        Timing timing = export("largeDocument", "A large document", Documents.largeDocument(30), portraitA4().build());

        assertThat(count(timing.docx(), "drawing")).isGreaterThanOrEqualTo(45);
        assertWithinReference(timing);
    }
}
