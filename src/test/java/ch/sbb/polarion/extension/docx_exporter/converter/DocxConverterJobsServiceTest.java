package ch.sbb.polarion.extension.docx_exporter.converter;

import ch.sbb.polarion.extension.docx_exporter.model.DebugData;
import ch.sbb.polarion.extension.docx_exporter.rest.model.conversion.ExportParams;
import ch.sbb.polarion.extension.docx_exporter.util.DebugDataStorage;
import ch.sbb.polarion.extension.docx_exporter.util.ExportContext;
import ch.sbb.polarion.extension.generic.jobs.JobState;
import ch.sbb.polarion.extension.generic.jobs.JobsRegistry;
import ch.sbb.polarion.extension.generic.rest.filter.LogoutFilter;
import ch.sbb.polarion.extension.generic.rest.model.jobs.JobStatus;
import com.polarion.platform.security.ISecurityService;
import org.awaitility.Durations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.security.auth.Subject;
import java.security.PrivilegedAction;
import java.time.Instant;
import java.util.ConcurrentModificationException;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocxConverterJobsServiceTest {

    private static final String TEST_USER = "testUser";

    @Mock
    private DocxConverter docxConverter;

    @Mock
    private ISecurityService securityService;

    @Mock
    private Subject subject;

    @Mock
    ServletRequestAttributes requestAttributes;

    private JobsRegistry<DocxConverterJobsService.JobPayload, byte[]> registry;
    private DocxConverterJobsService docxConverterJobsService;

    @BeforeEach
    void setup() {
        RequestContextHolder.setRequestAttributes(requestAttributes);
        docxConverterJobsService = jobsService(TimeUnit.MINUTES);
    }

    @AfterEach
    void tearDown() {
        registry.clear();
        registry.shutdown();
        DebugDataStorage.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    /**
     * A service over a registry of its own, set up as the one of the extension, so that no test leaves jobs behind
     * for the next one. A timeout unit shorter than minutes lets a test see a job run out of time.
     */
    private DocxConverterJobsService jobsService(TimeUnit timeoutUnit) {
        if (registry != null) {
            registry.shutdown();
        }
        registry = DocxConverterJobsService.registryBuilder().timeoutUnit(timeoutUnit).build();
        return new DocxConverterJobsService(docxConverter, securityService, registry);
    }

    @Test
    void shouldStartJobAndGetStatus() {
        prepareSecurityServiceSubject(subject);
        when(requestAttributes.getAttribute(LogoutFilter.XSRF_SKIP_LOGOUT, RequestAttributes.SCOPE_REQUEST)).thenReturn(Boolean.FALSE);
        when(requestAttributes.getAttribute(LogoutFilter.ASYNC_SKIP_LOGOUT, RequestAttributes.SCOPE_REQUEST)).thenReturn(Boolean.TRUE);
        ExportParams exportParams = ExportParams.builder().build();
        when(docxConverter.convertToDocx(exportParams)).thenReturn("test docx".getBytes());

        String jobId = docxConverterJobsService.startJob(exportParams, 60);

        assertThat(jobId).isNotBlank();
        waitToFinishJob(jobId);
        assertEquals(1, docxConverterJobsService.getAllJobsStates().size());
        JobState jobState = docxConverterJobsService.getJobState(jobId);
        assertThat(jobState.status()).isEqualTo(JobStatus.SUCCESSFULLY_FINISHED);
        Optional<byte[]> jobResult = docxConverterJobsService.getJobResult(jobId);
        assertThat(jobResult).isNotEmpty();
        assertThat(new String(jobResult.get())).isEqualTo("test docx");

        // Second attempt to ensure that job is not removed
        assertThat(docxConverterJobsService.getJobState(jobId).status()).isEqualTo(JobStatus.SUCCESSFULLY_FINISHED);
        assertThat(docxConverterJobsService.getJobResult(jobId)).isNotEmpty();
        assertThat(docxConverterJobsService.getJobParams(jobId)).isSameAs(exportParams);
        assertNotNull(docxConverterJobsService.getJobContext(jobId));

        // check unknown job ID
        assertThrows(NoSuchElementException.class, () -> docxConverterJobsService.getJobResult("unknownJobId"));

        await().atMost(Durations.FIVE_SECONDS).untilAsserted(() -> verify(securityService).logout(subject));

        // check job is not accessible for other users
        when(securityService.getCurrentUser()).thenReturn("other_" + TEST_USER);
        assertThrows(NoSuchElementException.class, () -> docxConverterJobsService.getJobResult(jobId));
        assertThrows(NoSuchElementException.class, () -> docxConverterJobsService.getJobState(jobId));
        assertThrows(NoSuchElementException.class, () -> docxConverterJobsService.getJobParams(jobId));
        assertThrows(NoSuchElementException.class, () -> docxConverterJobsService.getJobContext(jobId));
        assertTrue(docxConverterJobsService.getAllJobsStates().isEmpty());

        // double check that job is still accessible for the user who started it
        when(securityService.getCurrentUser()).thenReturn(TEST_USER);
        assertDoesNotThrow(() -> docxConverterJobsService.getJobResult(jobId));
        assertDoesNotThrow(() -> docxConverterJobsService.getJobState(jobId));
        assertDoesNotThrow(() -> docxConverterJobsService.getJobParams(jobId));
        assertDoesNotThrow(() -> docxConverterJobsService.getJobContext(jobId));
        assertEquals(1, docxConverterJobsService.getAllJobsStates().size());
    }

    /**
     * The conversion stores its debug data under the ID of its job, and the result names what its export context
     * collected: the thread-bound state of the worker is handed over to the job, then cleared for the next job.
     */
    @Test
    void shouldHandOverWorkerStateToJob() {
        prepareSecurityServiceSubject(subject);
        ExportParams exportParams = ExportParams.builder().build();
        AtomicReference<String> jobIdSeenByConversion = new AtomicReference<>();
        when(docxConverter.convertToDocx(exportParams)).thenAnswer(invocation -> {
            jobIdSeenByConversion.set(DebugDataStorage.getCurrentJobId());
            ExportContext.addWorkItemIDsWithMissingAttachment("EL-1");
            ExportContext.addBlockedResource("https://example.com/image.png", "not allowed");
            return "test docx".getBytes();
        });

        String jobId = docxConverterJobsService.startJob(exportParams, 60);

        waitToFinishJob(jobId);
        assertThat(jobIdSeenByConversion).hasValue(jobId);
        DocxConverterJobsService.JobContext jobContext = docxConverterJobsService.getJobContext(jobId);
        assertThat(jobContext.workItemIDsWithMissingAttachment()).containsExactly("EL-1");
        assertThat(jobContext.blockedResources()).hasSize(1);
    }

    @Test
    void shouldReturnFailInExceptionalCase() {
        prepareSecurityServiceSubject(subject);
        when(requestAttributes.getAttribute(LogoutFilter.XSRF_SKIP_LOGOUT, RequestAttributes.SCOPE_REQUEST)).thenReturn(Boolean.FALSE);
        when(requestAttributes.getAttribute(LogoutFilter.ASYNC_SKIP_LOGOUT, RequestAttributes.SCOPE_REQUEST)).thenReturn(Boolean.TRUE);
        ExportParams exportParams = ExportParams.builder().build();
        when(docxConverter.convertToDocx(exportParams)).thenThrow(new RuntimeException("test error"));

        String jobId = docxConverterJobsService.startJob(exportParams, 60);

        assertThat(jobId).isNotBlank();
        waitToFinishJob(jobId);
        JobState jobState = docxConverterJobsService.getJobState(jobId);
        assertThat(jobState.status()).isEqualTo(JobStatus.FAILED);
        assertThat(jobState.errorMessage()).isEqualTo("test error");

        assertThatThrownBy(() -> docxConverterJobsService.getJobResult(jobId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("test error");
        await().atMost(Durations.FIVE_SECONDS).untilAsserted(() -> verify(securityService).logout(subject));
    }

    @Test
    void shouldRecordRealReasonWhenExceptionMessageIsNull() {
        // Regression: an exception with a null message (e.g. ConcurrentModificationException thrown deep in
        // Polarion's ImportExportStatusKeeper) must not hide the real cause behind a secondary NullPointerException.
        prepareSecurityServiceSubject(subject);
        ExportParams exportParams = ExportParams.builder().build();
        when(docxConverter.convertToDocx(exportParams)).thenThrow(new ConcurrentModificationException());

        String jobId = docxConverterJobsService.startJob(exportParams, 60);

        waitToFinishJob(jobId);
        JobState jobState = docxConverterJobsService.getJobState(jobId);
        assertThat(jobState.status()).isEqualTo(JobStatus.FAILED);
        assertThat(jobState.errorMessage()).isEqualTo(ConcurrentModificationException.class.getName());
    }

    @Test
    void namesTheFailureRatherThanItsWrapper() {
        // the message of this one is stored as the reason of a failed job, and the export dialog
        // shows it: a CompletionException would put its own class name in front of the text
        prepareSecurityServiceSubject(subject);
        ExportParams exportParams = ExportParams.builder().build();
        when(docxConverter.convertToDocx(exportParams)).thenThrow(new CompletionException(new IllegalStateException("the secret holds nothing")));

        String jobId = docxConverterJobsService.startJob(exportParams, 60);

        waitToFinishJob(jobId);
        assertThat(docxConverterJobsService.getJobState(jobId).errorMessage()).isEqualTo("the secret holds nothing");
    }

    @Test
    void shouldGetAllJobsStatuses() {
        prepareSecurityServiceSubject(subject);
        ExportParams exportParams = ExportParams.builder().build();
        lenient().when(docxConverter.convertToDocx(exportParams)).thenReturn("test docx".getBytes());

        String jobId1 = docxConverterJobsService.startJob(exportParams, 60);
        String jobId2 = docxConverterJobsService.startJob(exportParams, 60);

        Map<String, JobState> allJobsStates = docxConverterJobsService.getAllJobsStates();
        assertThat(allJobsStates).containsOnlyKeys(jobId1, jobId2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldAcceptNullSubject() {
        prepareSecurityServiceSubject(null);
        ExportParams exportParams = ExportParams.builder().build();
        when(docxConverter.convertToDocx(exportParams)).thenReturn("test docx".getBytes());

        String jobId = docxConverterJobsService.startJob(exportParams, 60);

        waitToFinishJob(jobId);
        assertThat(docxConverterJobsService.getJobState(jobId).status()).isEqualTo(JobStatus.SUCCESSFULLY_FINISHED);
        // a job without a subject runs as it is, the way it would have run on the request thread
        verify(securityService, never()).doAsUser(any(), any(PrivilegedAction.class));
        verify(securityService, never()).logout(any());
    }

    @ParameterizedTest
    @CsvSource({
            "true,true",
            "true,false",
            "false,false"
    })
    void shouldNotLogoutWithoutAsyncSkipLogoutProperty(boolean xsrfSkipLogout, boolean asyncSkipLogout) {
        prepareSecurityServiceSubject(subject);
        lenient().when(requestAttributes.getAttribute(LogoutFilter.XSRF_SKIP_LOGOUT, RequestAttributes.SCOPE_REQUEST)).thenReturn(xsrfSkipLogout);
        lenient().when(requestAttributes.getAttribute(LogoutFilter.ASYNC_SKIP_LOGOUT, RequestAttributes.SCOPE_REQUEST)).thenReturn(asyncSkipLogout);
        ExportParams exportParams = ExportParams.builder().build();
        when(docxConverter.convertToDocx(exportParams)).thenReturn("test docx".getBytes());

        String jobId = docxConverterJobsService.startJob(exportParams, 60);

        waitToFinishJob(jobId);
        assertThat(docxConverterJobsService.getJobState(jobId).status()).isEqualTo(JobStatus.SUCCESSFULLY_FINISHED);
        verify(securityService, never()).logout(subject);
    }

    @Test
    void shouldFailJobOnTimeout() {
        docxConverterJobsService = jobsService(TimeUnit.MILLISECONDS);
        prepareSecurityServiceSubject(subject);
        ExportParams exportParams = ExportParams.builder().build();
        when(docxConverter.convertToDocx(exportParams)).thenAnswer(invocation -> sleepUntilInterrupted());

        String jobId = docxConverterJobsService.startJob(exportParams, 50);

        waitToFinishJob(jobId);
        JobState jobState = docxConverterJobsService.getJobState(jobId);
        assertThat(jobState.status()).isEqualTo(JobStatus.FAILED);
        assertThat(jobState.errorMessage()).isEqualTo("Timeout after 50 min");
    }

    @Test
    void shouldKeepRunningJobWithinTimeout() {
        prepareSecurityServiceSubject(subject);
        ExportParams exportParams = ExportParams.builder().build();
        when(docxConverter.convertToDocx(exportParams)).thenAnswer(invocation -> sleepUntilInterrupted());

        String jobId = docxConverterJobsService.startJob(exportParams, 1);
        await().atMost(Durations.FIVE_SECONDS).untilAsserted(() -> verify(docxConverter).convertToDocx(exportParams));

        assertThat(docxConverterJobsService.getJobState(jobId).status()).isEqualTo(JobStatus.IN_PROGRESS);
        assertThat(docxConverterJobsService.getJobResult(jobId)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"0,0", "1,1"})
    void shouldCleanupSuccessfullyFinishedJobs(int timeout, int expectedJobsCount) {
        prepareSecurityServiceSubject(subject);
        ExportParams exportParams = ExportParams.builder().build();
        when(docxConverter.convertToDocx(exportParams)).thenReturn("test docx".getBytes());
        String finishedJobId = docxConverterJobsService.startJob(exportParams, 1);
        waitToFinishJob(finishedJobId);

        assertJobsCountAfterCleanup(timeout, expectedJobsCount);
    }

    @ParameterizedTest
    @CsvSource({"0,0", "1,1"})
    void shouldCleanupFailedJobs(int timeout, int expectedJobsCount) {
        prepareSecurityServiceSubject(subject);
        ExportParams exportParams = ExportParams.builder().build();
        when(docxConverter.convertToDocx(exportParams)).thenThrow(new RuntimeException("test error"));
        String failedJobId = docxConverterJobsService.startJob(exportParams, 1);
        waitToFinishJob(failedJobId);
        assertThat(docxConverterJobsService.getJobState(failedJobId).errorMessage()).isEqualTo("test error");

        assertJobsCountAfterCleanup(timeout, expectedJobsCount);
    }

    @Test
    void shouldCleanupTimedOutInProgressJobs() {
        docxConverterJobsService = jobsService(TimeUnit.MILLISECONDS);
        prepareSecurityServiceSubject(subject);
        ExportParams exportParams = ExportParams.builder().build();
        when(docxConverter.convertToDocx(exportParams)).thenAnswer(invocation -> sleepUntilInterrupted());
        String jobId = docxConverterJobsService.startJob(exportParams, 1);
        waitToFinishJob(jobId);
        assertThat(docxConverterJobsService.getJobState(jobId).errorMessage()).isEqualTo("Timeout after 1 min");

        assertJobsCountAfterCleanup(0, 0);
    }

    /**
     * The debug data of a conversion is kept as long as its job, and goes together with it.
     */
    @Test
    void shouldCleanupDebugDataWithItsJob() {
        prepareSecurityServiceSubject(subject);
        ExportParams exportParams = ExportParams.builder().build();
        when(docxConverter.convertToDocx(exportParams)).thenReturn("test docx".getBytes());
        String jobId = docxConverterJobsService.startJob(exportParams, 1);
        waitToFinishJob(jobId);
        DebugDataStorage.store(jobId, DebugData.builder().user(TEST_USER).createdAt(Instant.now()).build());

        assertJobsCountAfterCleanup(0, 0);

        assertThat(DebugDataStorage.exists(jobId)).isFalse();
    }

    /**
     * A finished job expires once it is older than the timeout, which takes a moment even for a timeout of 0.
     */
    private void assertJobsCountAfterCleanup(int timeout, int expectedJobsCount) {
        await().atMost(Durations.FIVE_SECONDS).untilAsserted(() -> {
            registry.cleanupExpiredJobs(timeout);
            assertThat(docxConverterJobsService.getAllJobsStates()).hasSize(expectedJobsCount);
        });
    }

    private void waitToFinishJob(String jobId) {
        await().atMost(Durations.FIVE_SECONDS)
                .untilAsserted(() -> assertThat(docxConverterJobsService.getJobState(jobId).isDone()).isTrue());
    }

    /**
     * Blocks the way a long conversion does, until its thread is interrupted. The latch is never released.
     */
    private static byte[] sleepUntilInterrupted() {
        try {
            boolean released = new CountDownLatch(1).await(10, TimeUnit.MINUTES);
            throw new IllegalStateException("not interrupted, latch released: " + released);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        throw new IllegalStateException("interrupted");
    }

    @SuppressWarnings("unchecked")
    private void prepareSecurityServiceSubject(Subject userSubject) {
        lenient().when(securityService.getCurrentUser()).thenReturn(TEST_USER);
        lenient().when(securityService.getCurrentSubject()).thenReturn(userSubject);
        lenient().when(securityService.doAsUser(eq(userSubject), any(PrivilegedAction.class))).thenAnswer(invocation ->
                ((PrivilegedAction<?>) invocation.getArgument(1)).run());
    }
}
