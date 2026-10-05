package ch.sbb.polarion.extension.docx_exporter.converter;

import ch.sbb.polarion.extension.docx_exporter.rest.model.conversion.ExportParams;
import ch.sbb.polarion.extension.docx_exporter.util.DebugDataStorage;
import ch.sbb.polarion.extension.docx_exporter.util.ExportContext;
import ch.sbb.polarion.extension.generic.jobs.AsyncJobsService;
import ch.sbb.polarion.extension.generic.jobs.JobsProperties;
import ch.sbb.polarion.extension.generic.jobs.JobsRegistry;
import ch.sbb.polarion.extension.generic.jobs.TimeoutPolicy;
import com.polarion.platform.security.ISecurityService;
import lombok.Builder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Runs DOCX conversions in the background. The job mechanics are generic's {@link AsyncJobsService}; this class adds
 * the debug data and the export context of a conversion.
 */
public class DocxConverterJobsService extends AsyncJobsService<DocxConverterJobsService.JobPayload, byte[]> {

    public static final String JOBS_PROPERTIES_FILE = "/docx-converter-jobs.properties";

    // Static, so that the jobs survive the controller instance which started them
    private static final JobsRegistry<JobPayload, byte[]> REGISTRY = registryBuilder().build();

    private final DocxConverter docxConverter;

    public DocxConverterJobsService(@NotNull DocxConverter docxConverter, @NotNull ISecurityService securityService) {
        this(docxConverter, securityService, REGISTRY);
    }

    @VisibleForTesting
    DocxConverterJobsService(@NotNull DocxConverter docxConverter, @NotNull ISecurityService securityService,
                             @NotNull JobsRegistry<JobPayload, byte[]> registry) {
        super(registry, securityService);
        this.docxConverter = docxConverter;
    }

    /**
     * @return the job timeouts of this extension
     */
    public static @NotNull JobsProperties jobsProperties() {
        return new JobsProperties(DocxConverterJobsService.class, JOBS_PROPERTIES_FILE);
    }

    /**
     * Starts dropping finished conversions, and their debug data, once they are older than the finished job timeout.
     */
    public static void startCleaner() {
        REGISTRY.startCleaner(jobsProperties().getFinishedJobTimeout());
    }

    /**
     * Stops the cleaner and the conversion threads. Called when the bundle stops.
     */
    public static void shutdown() {
        REGISTRY.shutdown();
    }

    public @NotNull String startJob(@NotNull ExportParams exportParams, int timeoutInMinutes) {
        JobContext jobContext = JobContext.builder()
                .workItemIDsWithMissingAttachment(new ArrayList<>())
                .blockedResources(new ArrayList<>())
                .build();
        return startJob(new JobPayload(exportParams, jobContext), timeoutInMinutes, control -> {
            try {
                DebugDataStorage.setCurrentJobId(control.jobId());
                return docxConverter.convertToDocx(exportParams);
            } finally {
                DebugDataStorage.clearCurrentJobId();
                jobContext.workItemIDsWithMissingAttachment().addAll(ExportContext.getWorkItemIDsWithMissingAttachment());
                jobContext.blockedResources().addAll(ExportContext.getBlockedResources());
                ExportContext.clear();
            }
        });
    }

    public @NotNull ExportParams getJobParams(@NotNull String jobId) {
        return payload(jobId).exportParams();
    }

    public @NotNull JobContext getJobContext(@NotNull String jobId) {
        return payload(jobId).jobContext();
    }

    private @NotNull JobPayload payload(@NotNull String jobId) {
        return Objects.requireNonNull(getJobPayload(jobId), "Job payload is always set by startJob");
    }

    /**
     * A conversion only reads, so it is declared over at its timeout and its thread is interrupted.
     * Its debug data goes together with the job.
     */
    @VisibleForTesting
    static @NotNull JobsRegistry.Builder<JobPayload, byte[]> registryBuilder() {
        return JobsRegistry.<JobPayload, byte[]>builder("DOCX conversion")
                .timeoutPolicy(TimeoutPolicy.INTERRUPT)
                .onJobRemoved(DebugDataStorage::remove)
                .onCleanup(DebugDataStorage::cleanupExpired);
    }

    /**
     * What a conversion keeps next to its result: its parameters, and what its export context collected.
     */
    public record JobPayload(@NotNull ExportParams exportParams, @NotNull JobContext jobContext) {
    }

    @Builder
    public record JobContext(
            List<String> workItemIDsWithMissingAttachment,
            List<ExportContext.BlockedResource> blockedResources) {
    }
}
