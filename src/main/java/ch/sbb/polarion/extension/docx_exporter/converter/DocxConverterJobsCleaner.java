package ch.sbb.polarion.extension.docx_exporter.converter;

import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class DocxConverterJobsCleaner {
    private static Future<?> cleaningJob;

    private DocxConverterJobsCleaner() {
    }

    public static synchronized void startCleaningJob() {
        if (cleaningJob != null) {
            return;
        }
        PropertiesUtility propertiesUtility = new PropertiesUtility();

        ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
        cleaningJob = executorService.scheduleWithFixedDelay(
                () -> DocxConverterJobsService.cleanupExpiredJobs(propertiesUtility.getFinishedJobTimeout()),
                0,
                propertiesUtility.getFinishedJobTimeout(),
                TimeUnit.MINUTES);
    }
}
