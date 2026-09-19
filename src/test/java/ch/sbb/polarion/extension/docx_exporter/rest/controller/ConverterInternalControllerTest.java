package ch.sbb.polarion.extension.docx_exporter.rest.controller;

import ch.sbb.polarion.extension.docx_exporter.converter.DocxConverterJobsService;
import ch.sbb.polarion.extension.docx_exporter.converter.DocxConverterJobsService.JobState;
import ch.sbb.polarion.extension.docx_exporter.converter.HtmlToDocxConverter;
import ch.sbb.polarion.extension.docx_exporter.rest.model.conversion.ExportParams;
import ch.sbb.polarion.extension.docx_exporter.rest.model.jobs.ConverterJobDetails;
import ch.sbb.polarion.extension.docx_exporter.rest.model.jobs.ConverterJobStatus;
import ch.sbb.polarion.extension.docx_exporter.service.DocxExporterPolarionService;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import ch.sbb.polarion.extension.docx_exporter.util.ExportContext;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConverterInternalControllerTest {
    @Mock
    private DocxConverterJobsService docxConverterJobService;
    @Mock
    private UriInfo uriInfo;
    @Mock
    private DocxExporterPolarionService docxExporterPolarionService;
    @Mock
    private HtmlToDocxConverter htmlToDocxConverter;

    @InjectMocks
    private ConverterInternalController internalController;

    @BeforeEach
    void authorizeExportByDefault() {
        lenient().when(docxExporterPolarionService.userAuthorizedForExport(nullable(String.class))).thenReturn(true);
    }

    @Test
    void convertHtmlToPdf_namesTheResourcesWhichWereNotEmbedded() {
        // the html sent here names resources of its own and the policy refuses them the same way it does
        // for a document: the answer is the only place where the sender learns what the file did not get
        ExportContext.clear();
        FormDataBodyPart html = mock(FormDataBodyPart.class);
        when(html.getEntityAs(InputStream.class)).thenReturn(new ByteArrayInputStream("<html><body>text</body></html>".getBytes(StandardCharsets.UTF_8)));
        when(htmlToDocxConverter.convert(anyString(), nullable(byte[].class), any())).thenAnswer(invocation -> {
            ExportContext.addBlockedResource("http://host/x.png", "it was refused");
            return "test docx".getBytes();
        });

        Response response = internalController.convertHtmlToPdf(html, null, null, null, null);

        assertThat(response.getHeaderString("Blocked-Resources-Count")).isEqualTo("1");
        assertThat(response.getHeaderString("Blocked-Resources")).isEqualTo("http://host/x.png");
        ExportContext.clear();
    }

    @Test
    void getExportPermission_returnsPermittedFlag() {
        try (Response response = internalController.getExportPermission("testProjectId")) {
            assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
            assertThat((Boolean) ((Map<?, ?>) response.getEntity()).get("permitted")).isTrue();
        }
    }

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
        when(docxConverterJobService.getJobContext("testJobId")).thenReturn(DocxConverterJobsService.JobContext.builder().workItemIDsWithMissingAttachment(new ArrayList<String>()).blockedResources(new ArrayList<>()).build());
        Response jobResult = internalController.getPdfConverterJobResult("testJobId");

        assertThat(jobResult.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(jobResult.getEntity()).isEqualTo("test docx".getBytes());
    }

    @Test
    void getPdfConverterJobResult_namesTheResourcesWhichWereNotEmbedded() {
        // the PDF was produced without them, and nothing in it says so: the result of the conversion does
        when(docxConverterJobService.getJobResult("testJobId")).thenReturn(Optional.of("test pdf".getBytes()));
        when(docxConverterJobService.getJobContext("testJobId")).thenReturn(DocxConverterJobsService.JobContext.builder()
                .workItemIDsWithMissingAttachment(new ArrayList<String>())
                .blockedResources(new ArrayList<>(List.of(new ExportContext.BlockedResource("http://host/x.png", "it was refused"))))
                .build());

        Response jobResult = internalController.getPdfConverterJobResult("testJobId");

        assertThat(jobResult.getHeaderString("Blocked-Resources-Count")).isEqualTo("1");
        assertThat(jobResult.getHeaderString("Blocked-Resources")).isEqualTo("http://host/x.png");
    }

    @Test
    void getPdfConverterJobResult_keepsTheBlockedResourcesHeaderWithinWhatAResponseCarries() {
        // the urls come out of a document, so nothing caps how many there are or what they carry: a line
        // break would end the header and a long enough list would carry the response past a container's limit
        List<ExportContext.BlockedResource> blocked = new ArrayList<>();
        blocked.add(new ExportContext.BlockedResource("http://host/with\r\na-line-break.png", "it was refused"));
        for (int index = 1; index < 15; index++) {
            blocked.add(new ExportContext.BlockedResource("http://host/" + "x".repeat(300) + index + ".png", "it was refused"));
        }
        when(docxConverterJobService.getJobResult("testJobId")).thenReturn(Optional.of("test pdf".getBytes()));
        when(docxConverterJobService.getJobContext("testJobId")).thenReturn(DocxConverterJobsService.JobContext.builder()
                .workItemIDsWithMissingAttachment(new ArrayList<String>())
                .blockedResources(blocked)
                .build());

        Response jobResult = internalController.getPdfConverterJobResult("testJobId");

        String header = jobResult.getHeaderString("Blocked-Resources");
        assertThat(jobResult.getHeaderString("Blocked-Resources-Count")).isEqualTo("15");
        assertThat(header).doesNotContain("\r").doesNotContain("\n").endsWith("and 5 more");
        assertThat(header.length()).isLessThan(2500);
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
