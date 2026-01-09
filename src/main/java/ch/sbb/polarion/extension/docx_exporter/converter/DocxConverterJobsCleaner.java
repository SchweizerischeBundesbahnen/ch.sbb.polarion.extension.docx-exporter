package ch.sbb.polarion.extension.docx_exporter.converter;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class DocxConverterJobsCleaner {
    private static ScheduledExecutorService executorService;

    private DocxConverterJobsCleaner() {
    }

    public static synchronized void startCleaningJob() {
        if (executorService != null) {
            return;
        }
        PropertiesUtility propertiesUtility = new PropertiesUtility();

        executorService = Executors.newSingleThreadScheduledExecutor();
        executorService.scheduleWithFixedDelay(
                () -> DocxConverterJobsService.cleanupExpiredJobs(propertiesUtility.getFinishedJobTimeout()),
                0,
                propertiesUtility.getFinishedJobTimeout(),
                TimeUnit.MINUTES);
    }

    public static synchronized void stopCleaningJob() {
        if (executorService != null) {
            executorService.shutdown();
            executorService = null;
        }
    }
}
