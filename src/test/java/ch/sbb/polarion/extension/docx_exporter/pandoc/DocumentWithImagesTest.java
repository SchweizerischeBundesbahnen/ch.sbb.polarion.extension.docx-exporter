package ch.sbb.polarion.extension.docx_exporter.pandoc;

/**
 * A document with many embedded images, small enough for the regular build: it takes the paths of the large one, in
 * seconds. {@link LargeDocumentWithImagesTest} converts one of 20 MB in the performance tests.
 */
class DocumentWithImagesTest extends BaseDocumentWithImagesTest {

    @Override
    protected int targetSizeMb() {
        return 1;
    }
}
