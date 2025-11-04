package ch.sbb.polarion.extension.docx_exporter.converter;

import ch.sbb.polarion.extension.generic.rest.filter.LogoutFilter;
import ch.sbb.polarion.extension.docx_exporter.converter.DocxConverterJobsService.JobState;
import ch.sbb.polarion.extension.docx_exporter.rest.model.conversion.ExportParams;
import com.polarion.platform.security.ISecurityService;
import org.awaitility.Durations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.security.auth.Subject;
import java.security.PrivilegedAction;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

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

    @InjectMocks
    private DocxConverterJobsService docxConverterJobsService;

    @BeforeEach
    void setup() {
        RequestContextHolder.setRequestAttributes(requestAttributes);
    }

    @AfterEach
    void tearDown() {
        docxConverterJobsService.cancelJobsAndCleanMap();
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
        assertThat(jobState.isCompletedExceptionally()).isFalse();
        assertThat(jobState.isCancelled()).isFalse();
        Optional<byte[]> jobResult = docxConverterJobsService.getJobResult(jobId);
        assertThat(jobResult).isNotEmpty();
        assertThat(new String(jobResult.get())).isEqualTo("test docx");

        // Second attempt to ensure that job is not removed
        jobState = docxConverterJobsService.getJobState(jobId);
        assertThat(jobState.isDone()).isTrue();
        assertThat(jobState.isCompletedExceptionally()).isFalse();
        assertThat(jobState.isCancelled()).isFalse();
        jobResult = docxConverterJobsService.getJobResult(jobId);
        assertThat(jobResult).isNotEmpty();
        assertNotNull(docxConverterJobsService.getJobContext(jobId));

        // check unknown job ID
        assertThrows(NoSuchElementException.class, () -> docxConverterJobsService.getJobResult("unknownJobId"));

        verify(securityService).logout(subject);

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
        assertThat(jobState.isCompletedExceptionally()).isTrue();
        assertThat(jobState.isCancelled()).isFalse();
        assertThat(jobState.errorMessage()).contains("test error");

        assertThatThrownBy(() -> docxConverterJobsService.getJobResult(jobId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("test error");
        verify(securityService).logout(subject);
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
    void shouldAcceptNullSubject() {
        prepareSecurityServiceSubject(null);

        ExportParams exportParams = ExportParams.builder().build();
        lenient().when(docxConverter.convertToDocx(exportParams)).thenReturn("test docx".getBytes());

        String jobId = docxConverterJobsService.startJob(exportParams, 60);

        assertThat(jobId).isNotBlank();
        waitToFinishJob(jobId);
        JobState jobState = docxConverterJobsService.getJobState(jobId);
        assertThat(jobState.isCompletedExceptionally()).isFalse();
        assertThat(jobState.isCancelled()).isFalse();
        verify(securityService, never()).logout(null);
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

        assertThat(jobId).isNotBlank();
        waitToFinishJob(jobId);
        JobState jobState = docxConverterJobsService.getJobState(jobId);
        assertThat(jobState.isCompletedExceptionally()).isFalse();
        assertThat(jobState.isCancelled()).isFalse();
        verify(securityService, never()).logout(subject);
    }

    @ParameterizedTest
    @CsvSource({"0,true", "1,false"})
    @SuppressWarnings({"unchecked", "java:S2925"})
    void shouldRespectInProgressTimeout(int timeout, boolean isTimeoutExpected) {
        ExportParams exportParams = ExportParams.builder().build();
        lenient().when(securityService.doAsUser(any(), any(PrivilegedAction.class))).thenAnswer(p -> {
            Thread.sleep(TimeUnit.MINUTES.toMillis(10));
            return null;
        });
        String jobId = docxConverterJobsService.startJob(exportParams, timeout);

        await().atMost(Durations.FIVE_SECONDS).untilAsserted(() -> {
            JobState jobState = docxConverterJobsService.getJobState(jobId);
            if (isTimeoutExpected) {
                assertThat(jobState.isDone()).isTrue();
                assertThat(jobState.isCompletedExceptionally()).isTrue();
                assertThat(jobState.errorMessage()).contains("Timeout after 0 min");
            } else {
                assertThat(jobState.isDone()).isFalse();
            }
        });
    }

    @ParameterizedTest
    @CsvSource({"0,0", "1,1"})
    void shouldCleanupSuccessfullyFinishedJobs(int timeout, int expectedJobsCount) {
        ExportParams exportParams = ExportParams.builder().build();
        String finishedJobId = docxConverterJobsService.startJob(exportParams, 1);
        waitToFinishJob(finishedJobId);

        DocxConverterJobsService.cleanupExpiredJobs(timeout);

        assertThat(docxConverterJobsService.getAllJobsStates()).hasSize(expectedJobsCount);
    }

    @ParameterizedTest
    @CsvSource({"0,0", "1,1"})
    void shouldCleanupFailedJobs(int timeout, int expectedJobsCount) {
        lenient().when(securityService.doAsUser(any(), any(PrivilegedAction.class))).thenThrow(new RuntimeException("test error"));
        ExportParams exportParams = ExportParams.builder().build();
        String failedJobId = docxConverterJobsService.startJob(exportParams, 1);
        waitToFinishJob(failedJobId);

        JobState jobState = docxConverterJobsService.getJobState(failedJobId);
        assertThat(jobState.isDone()).isTrue();
        assertThat(jobState.isCompletedExceptionally()).isTrue();
        assertThat(jobState.errorMessage()).contains("test error");

        DocxConverterJobsService.cleanupExpiredJobs(timeout);

        assertThat(docxConverterJobsService.getAllJobsStates()).hasSize(expectedJobsCount);
    }

    @Test
    @SuppressWarnings({"unchecked", "java:S2925"})
    void shouldCleanupTimedOutInProgressJobs() {
        ExportParams exportParams = ExportParams.builder().build();
        lenient().when(securityService.doAsUser(eq(null), any(PrivilegedAction.class))).thenAnswer(p -> {
            Thread.sleep(TimeUnit.MINUTES.toMillis(10));
            return null;
        });
        String jobId = docxConverterJobsService.startJob(exportParams, 0);
        await().atMost(Durations.FIVE_SECONDS).untilAsserted(() -> {
            JobState jobState = docxConverterJobsService.getJobState(jobId);
            assertThat(jobState.isDone()).isTrue();
            assertThat(jobState.isCompletedExceptionally()).isTrue();
            assertThat(jobState.errorMessage()).contains("Timeout after 0 min");
        });

        DocxConverterJobsService.cleanupExpiredJobs(0);

        assertThat(docxConverterJobsService.getAllJobsStates()).isEmpty();
    }

    private void waitToFinishJob(String jobId1) {
        await().atMost(Durations.FIVE_SECONDS)
                .untilAsserted(() -> {
                    JobState jobState = docxConverterJobsService.getJobState(jobId1);
                    assertThat(jobState.isDone()).isTrue();
                });
    }

    @SuppressWarnings("unchecked")
    private void prepareSecurityServiceSubject(Subject userSubject) {
        when(securityService.getCurrentUser()).thenReturn(TEST_USER);
        when(securityService.getCurrentSubject()).thenReturn(userSubject);
        when(securityService.doAsUser(eq(userSubject), any(PrivilegedAction.class))).thenAnswer(invocation ->
                ((PrivilegedAction<?>) invocation.getArgument(1)).run());
    }
}
