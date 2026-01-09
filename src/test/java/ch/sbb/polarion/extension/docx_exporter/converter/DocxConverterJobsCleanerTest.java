package ch.sbb.polarion.extension.docx_exporter.converter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class DocxConverterJobsCleanerTest {

    @AfterEach
    void tearDown() {
        DocxConverterJobsCleaner.stopCleaningJob();
    }

    @Test
    void shouldStartCleaningJob() {
        assertDoesNotThrow(DocxConverterJobsCleaner::startCleaningJob);
    }

    @Test
    void shouldNotFailWhenStartingJobTwice() {
        DocxConverterJobsCleaner.startCleaningJob();
        assertDoesNotThrow(DocxConverterJobsCleaner::startCleaningJob);
    }

    @Test
    void shouldStopCleaningJob() {
        DocxConverterJobsCleaner.startCleaningJob();
        assertDoesNotThrow(DocxConverterJobsCleaner::stopCleaningJob);
    }

    @Test
    void shouldNotFailWhenStoppingNotStartedJob() {
        assertDoesNotThrow(DocxConverterJobsCleaner::stopCleaningJob);
    }

    @Test
    void shouldAllowRestartAfterStop() {
        DocxConverterJobsCleaner.startCleaningJob();
        DocxConverterJobsCleaner.stopCleaningJob();
        assertDoesNotThrow(DocxConverterJobsCleaner::startCleaningJob);
    }
}
