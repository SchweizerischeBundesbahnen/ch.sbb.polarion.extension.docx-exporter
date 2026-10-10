package ch.sbb.polarion.extension.docx_exporter.pandoc;

import org.junit.jupiter.api.Tag;

/**
 * A document of 20 MB with embedded images. pandoc takes minutes and gigabytes of memory for it, so it runs in the
 * performance tests, not in the regular build: mvn verify -P performance-tests-with-pandoc-docker
 */
@Tag("performance")
class LargeDocumentWithImagesTest extends BaseDocumentWithImagesTest {

    @Override
    protected int targetSizeMb() {
        return 20;
    }
}
