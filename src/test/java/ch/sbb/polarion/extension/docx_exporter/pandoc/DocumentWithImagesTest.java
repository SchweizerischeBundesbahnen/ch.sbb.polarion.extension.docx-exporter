package ch.sbb.polarion.extension.docx_exporter.pandoc;

/**
 * A document with many embedded images, small enough for the regular build: it takes the paths of the large one, in
 * seconds. The performance tests time a large document with images ({@code ExportPerformanceTest}).
 */
class DocumentWithImagesTest extends BaseDocumentWithImagesTest {

    @Override
    protected int targetSizeMb() {
        return 1;
    }
}
