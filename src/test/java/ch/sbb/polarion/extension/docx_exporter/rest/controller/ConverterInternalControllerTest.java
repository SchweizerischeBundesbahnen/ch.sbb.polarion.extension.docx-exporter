package ch.sbb.polarion.extension.docx_exporter.rest.controller;

import ch.sbb.polarion.extension.docx_exporter.converter.DocxConverterJobsService;
import ch.sbb.polarion.extension.docx_exporter.converter.DocxConverterJobsService.JobState;
import ch.sbb.polarion.extension.docx_exporter.rest.model.conversion.ExportParams;
import ch.sbb.polarion.extension.docx_exporter.rest.model.jobs.ConverterJobDetails;
import ch.sbb.polarion.extension.docx_exporter.rest.model.jobs.ConverterJobStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import java.util.ArrayList;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConverterInternalControllerTest {
    @Mock
    private DocxConverterJobsService docxConverterJobService;
    @Mock
    private UriInfo uriInfo;

    @InjectMocks
    private ConverterInternalController internalController;

    @Test
    void startPdfConverterJob_success() {
        ExportParams params = ExportParams.builder()
                .projectId("testProjectId")
                .locationPath("testLocationPath")
                .build();
        when(docxConverterJobService.startJob(params, 60)).thenReturn("testJobId");
        when(uriInfo.getRequestUri()).thenReturn(UriBuilder.fromUri("http://testHost:8090/polarion/docx-exporter/rest/api/convert/jobs").build());
        try (Response response = internalController.startPdfConverterJob(params)) {
            assertThat(response.getStatus()).isEqualTo(HttpStatus.ACCEPTED.value());
            assertThat(response.getHeaderString(HttpHeaders.LOCATION)).isEqualTo("/polarion/docx-exporter/rest/api/convert/jobs/testJobId");
        }
    }

    @ParameterizedTest
    @MethodSource("getWrongConverterExportParams")
    void startPdfConverterJob_badRequest(ExportParams exportParams, String expectedErrorMessage) {
        assertThatThrownBy(() -> internalController.startPdfConverterJob(exportParams))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining(expectedErrorMessage);
    }

    public static Stream<Arguments> getWrongConverterExportParams() {
        return Stream.of(
                Arguments.of(null, "Missing export parameters"),
                Arguments.of(ExportParams.builder().locationPath("test").build(), "projectId"),
                Arguments.of(ExportParams.builder().projectId("test").build(), "locationPath")
        );
    }

    @ParameterizedTest
    @MethodSource("getStatusParams")
    void getPdfConverterJobStatus_success(JobState jobState,
                                          HttpStatus expectedHttpStatus,
                                          ConverterJobStatus expectedJobStatus,
                                          String expectedLocationUrl,
                                          String expectedErrorMessage) {
        if (expectedLocationUrl != null) {
            when(uriInfo.getRequestUri()).thenReturn(UriBuilder.fromUri("http://testHost:8090/polarion/docx-exporter/rest/api/convert/jobs/testJobId").build());
        }
        when(docxConverterJobService.getJobState("testJobId")).thenReturn(jobState);
        try (Response response = internalController.getPdfConverterJobStatus("testJobId")) {
            assertThat(response.getStatus()).isEqualTo(expectedHttpStatus.value());
            assertThat(response.getEntity()).isInstanceOf(ConverterJobDetails.class);
            assertThat(((ConverterJobDetails) response.getEntity()).getStatus()).isEqualTo(expectedJobStatus);
            if (expectedErrorMessage != null) {
                assertThat(((ConverterJobDetails) response.getEntity()).getErrorMessage()).contains(expectedErrorMessage);
            } else {
                assertThat(((ConverterJobDetails) response.getEntity()).getErrorMessage()).isNull();
            }
            assertThat(response.getHeaderString(HttpHeaders.LOCATION)).isEqualTo(expectedLocationUrl);
        }
    }

    static Stream<Arguments> getStatusParams() {
        return Stream.of(
                Arguments.of(new JobState(false, false, false, null), HttpStatus.ACCEPTED, ConverterJobStatus.IN_PROGRESS, null, null),
                Arguments.of(new JobState(true, false, false, null), HttpStatus.SEE_OTHER, ConverterJobStatus.SUCCESSFULLY_FINISHED, "/polarion/docx-exporter/rest/api/convert/jobs/testJobId/result", null),
                Arguments.of(new JobState(true, false, true, null), HttpStatus.CONFLICT, ConverterJobStatus.CANCELLED, null, null),
                Arguments.of(new JobState(true, true, false, "test error"), HttpStatus.CONFLICT, ConverterJobStatus.FAILED, null, "test error")
        );
    }

    @Test
    void getPdfConverterJobStatus_notFound() {
        when(docxConverterJobService.getJobState(anyString())).thenAnswer(id -> {
            throw new NoSuchElementException("Job not found: " + id);
        });
        assertThatThrownBy(() -> internalController.getPdfConverterJobStatus("testJobIdUnknown"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("testJobIdUnknown");
    }

    @Test
    void getPdfConverterJobResult_success() {
        when(docxConverterJobService.getJobResult("testJobId")).thenReturn(Optional.of("test docx".getBytes()));
        when(docxConverterJobService.getJobContext("testJobId")).thenReturn(DocxConverterJobsService.JobContext.builder().workItemIDsWithMissingAttachment(new ArrayList<String>()).build());
        Response jobResult = internalController.getPdfConverterJobResult("testJobId");

        assertThat(jobResult.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(jobResult.getEntity()).isEqualTo("test docx".getBytes());
    }

    @Test
    void getPdfConverterJobResult_notFound() {
        when(docxConverterJobService.getJobResult(anyString())).thenAnswer(id -> {
            throw new NoSuchElementException("Job not found: " + id);
        });
        assertThatThrownBy(() -> internalController.getPdfConverterJobResult("testJobIdUnknown"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("testJobIdUnknown");
    }

    @Test
    void getPdfConverterJobResult_illegalState() {
        when(docxConverterJobService.getJobResult("testJobId")).thenThrow(new IllegalStateException("Job was cancelled or failed: testJobId"));

        assertThatThrownBy(() -> internalController.getPdfConverterJobResult("testJobId"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Job was cancelled or failed: testJobId");
    }

    @Test
    void getAllPdfConverterJobs() {
        when(docxConverterJobService.getAllJobsStates()).thenReturn(
                Map.of(
                        "testJobId1", new JobState(true, false, false, null),
                        "testJobId2", new JobState(false, false, false, null),
                        "testJobId3", new JobState(true, true, false, "test error")
                )
        );

        Response response = internalController.getAllPdfConverterJobs();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.getEntity()).isEqualTo(
                Map.of(
                        "testJobId1", ConverterJobDetails.builder().status(ConverterJobStatus.SUCCESSFULLY_FINISHED).build(),
                        "testJobId2", ConverterJobDetails.builder().status(ConverterJobStatus.IN_PROGRESS).build(),
                        "testJobId3", ConverterJobDetails.builder().status(ConverterJobStatus.FAILED).errorMessage("test error").build()
                )
        );
    }
}
