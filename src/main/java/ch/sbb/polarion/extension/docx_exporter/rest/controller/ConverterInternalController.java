package ch.sbb.polarion.extension.docx_exporter.rest.controller;

import ch.sbb.polarion.extension.docx_exporter.converter.HtmlToDocxConverter;
import ch.sbb.polarion.extension.docx_exporter.converter.DocxConverter;
import ch.sbb.polarion.extension.docx_exporter.converter.DocxConverterJobsService;
import ch.sbb.polarion.extension.docx_exporter.converter.DocxConverterJobsService.JobState;
import ch.sbb.polarion.extension.docx_exporter.converter.PropertiesUtility;
import ch.sbb.polarion.extension.docx_exporter.pandoc.service.model.PandocParams;
import ch.sbb.polarion.extension.docx_exporter.rest.model.NestedListsCheck;
import ch.sbb.polarion.extension.docx_exporter.rest.model.conversion.ExportParams;
import ch.sbb.polarion.extension.docx_exporter.rest.model.documents.DocumentData;
import ch.sbb.polarion.extension.docx_exporter.rest.model.jobs.ConverterJobDetails;
import ch.sbb.polarion.extension.docx_exporter.rest.model.jobs.ConverterJobStatus;
import ch.sbb.polarion.extension.docx_exporter.util.DocumentDataFactory;
import ch.sbb.polarion.extension.docx_exporter.util.DocumentFileNameHelper;
import ch.sbb.polarion.extension.docx_exporter.util.ExportContext;
import ch.sbb.polarion.extension.docx_exporter.util.NumberedListsSanitizer;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.core.util.StringUtils;
import com.polarion.platform.core.PlatformContext;
import com.polarion.platform.security.ISecurityService;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.springframework.http.HttpStatus;

import javax.ws.rs.BadRequestException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Hidden
@Path("/internal")
@Tag(name = "DOCX Processing")
@SuppressWarnings("java:S1200")
public class ConverterInternalController {

    private static final String EXPORT_FILENAME_HEADER = "Export-Filename";

    private static final String MISSING_WORKITEM_ATTACHMENTS_COUNT = "Missing-WorkItem-Attachments-Count";
    private static final String WORKITEM_IDS_WITH_MISSING_ATTACHMENT = "WorkItem-IDs-With-Missing-Attachment";

    private final DocxConverter docxConverter;
    private final DocxConverterJobsService pdfConverterJobService;
    private final PropertiesUtility propertiesUtility;
    private final HtmlToDocxConverter htmlToDocxConverter;

    @Context
    private UriInfo uriInfo;

    public ConverterInternalController() {
        this.docxConverter = new DocxConverter();
        ISecurityService securityService = PlatformContext.getPlatform().lookupService(ISecurityService.class);
        this.pdfConverterJobService = new DocxConverterJobsService(docxConverter, securityService);
        this.propertiesUtility = new PropertiesUtility();
        this.htmlToDocxConverter = new HtmlToDocxConverter();
    }

    @VisibleForTesting
    ConverterInternalController(DocxConverter docxConverter, DocxConverterJobsService pdfConverterJobService, UriInfo uriInfo, HtmlToDocxConverter htmlToDocxConverter) {
        this.docxConverter = docxConverter;
        this.pdfConverterJobService = pdfConverterJobService;
        this.uriInfo = uriInfo;
        this.propertiesUtility = new PropertiesUtility();
        this.htmlToDocxConverter = htmlToDocxConverter;
    }

    @POST
    @Path("/convert")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    @Operation(summary = "Returns requested Polarion's document converted to DOCX",
            requestBody = @RequestBody(description = "Export parameters to generate the DOCX",
                    required = true,
                    content = @Content(schema = @Schema(implementation = ExportParams.class),
                            mediaType = MediaType.APPLICATION_JSON
                    )
            ),
            responses = {
                    @ApiResponse(responseCode = "200",
                            description = "Content of DOCX document as a byte array",
                            content = {@Content(mediaType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document")},
                            headers = {
                                    @Header(name = HttpHeaders.CONTENT_DISPOSITION,
                                            description = "To inform a browser that the response is a downloadable attachment",
                                            schema = @Schema(implementation = String.class)
                                    ),
                                    @Header(name = EXPORT_FILENAME_HEADER,
                                            description = "File name for converted DOCX document",
                                            schema = @Schema(implementation = String.class)
                                    ),
                                    @Header(name = MISSING_WORKITEM_ATTACHMENTS_COUNT,
                                            description = "Unavailable work item attachments count",
                                            schema = @Schema(implementation = String.class)
                                    ),
                                    @Header(name = WORKITEM_IDS_WITH_MISSING_ATTACHMENT,
                                            description = "Work items contained unavailable attachments",
                                            schema = @Schema(implementation = String.class)
                                    )
                            }
                    )
            })
    public Response convertToDocx(ExportParams exportParams) {
        validateExportParameters(exportParams);
        String fileName = getFileName(exportParams);
        byte[] pdfBytes = docxConverter.convertToDocx(exportParams);
        return Response.ok(pdfBytes)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .header(EXPORT_FILENAME_HEADER, fileName)
                .header(MISSING_WORKITEM_ATTACHMENTS_COUNT, ExportContext.getWorkItemIDsWithMissingAttachment().size())
                .header(WORKITEM_IDS_WITH_MISSING_ATTACHMENT, ExportContext.getWorkItemIDsWithMissingAttachment())
                .build();
    }

    @POST
    @Path("/prepared-html-content")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_HTML)
    @Operation(summary = "Returns prepared HTML which will be used for DOCX conversion using Pandoc",
            requestBody = @RequestBody(description = "Export parameters to generate the DOCX",
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = ExportParams.class),
                            mediaType = MediaType.APPLICATION_JSON
                    )
            ),
            responses = {
                    @ApiResponse(responseCode = "200",
                            description = "Prepared HTML content",
                            content = {@Content(mediaType = MediaType.TEXT_HTML)}
                    )
            })
    public String prepareHtmlContent(ExportParams exportParams) {
        validateExportParameters(exportParams);
        return docxConverter.prepareHtmlContent(exportParams);
    }

    @POST
    @Path("/convert/jobs")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Starts asynchronous conversion job of Polarion's document to DOCX",
            requestBody = @RequestBody(description = "Export parameters to generate the DOCX",
                    required = true,
                    content = @Content(schema = @Schema(implementation = ExportParams.class),
                            mediaType = MediaType.APPLICATION_JSON
                    )
            ),
            responses = {
                    @ApiResponse(responseCode = "202",
                            description = "Conversion process is started, job URI is returned in Location header"
                    )
            })
    public Response startPdfConverterJob(ExportParams exportParams) {
        validateExportParameters(exportParams);

        String jobId = pdfConverterJobService.startJob(exportParams, propertiesUtility.getInProgressJobTimeout());

        URI jobUri = UriBuilder.fromUri(uriInfo.getRequestUri().getPath()).path(jobId).build();
        return Response.accepted().location(jobUri).build();
    }

    @GET
    @Path("/convert/jobs/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Returns DOCX conversion job status",
            responses = {
                    // OpenAPI response MediaTypes for 303 and 202 response codes are generic to satisfy automatic redirect in SwaggerUI
                    @ApiResponse(responseCode = "303",
                            description = "Conversion job is finished successfully, Location header contains result URL",
                            content = {@Content(mediaType = "application/*", schema = @Schema(implementation = ConverterJobDetails.class))}
                    ),
                    @ApiResponse(responseCode = "202",
                            description = "Conversion job is still in progress",
                            content = {@Content(mediaType = "application/*", schema = @Schema(implementation = ConverterJobDetails.class))}
                    ),
                    @ApiResponse(responseCode = "409",
                            description = "Conversion job is failed or cancelled"
                    ),
                    @ApiResponse(responseCode = "404",
                            description = "Conversion job id is unknown"
                    )
            })
    public Response getPdfConverterJobStatus(@PathParam("id") String jobId) {
        JobState jobState = pdfConverterJobService.getJobState(jobId);

        ConverterJobStatus converterJobStatus = convertToJobStatus(jobState);
        ConverterJobDetails jobDetails = ConverterJobDetails.builder()
                .status(converterJobStatus)
                .errorMessage(jobState.errorMessage()).build();

        Response.ResponseBuilder responseBuilder;
        switch (converterJobStatus) {
            case IN_PROGRESS -> responseBuilder = Response.accepted();
            case SUCCESSFULLY_FINISHED -> {
                URI jobUri = UriBuilder.fromUri(uriInfo.getRequestUri().getPath()).path("result").build();
                responseBuilder = Response.status(HttpStatus.SEE_OTHER.value()).location(jobUri);
            }
            default -> responseBuilder = Response.status(HttpStatus.CONFLICT.value());
        }
        return responseBuilder.entity(jobDetails).build();
    }

    @GET
    @Path("/convert/jobs/{id}/result")
    @Produces("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    @Operation(summary = "Returns DOCX conversion job result",
            responses = {
                    @ApiResponse(responseCode = "200",
                            description = "Conversion job result is ready",
                            content = {@Content(mediaType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document")},
                            headers = {
                                    @Header(name = HttpHeaders.CONTENT_DISPOSITION,
                                            description = "To inform a browser that the response is a downloadable attachment",
                                            schema = @Schema(implementation = String.class)
                                    ),
                                    @Header(name = EXPORT_FILENAME_HEADER,
                                            description = "File name for converted DOCX document",
                                            schema = @Schema(implementation = String.class)
                                    ),
                                    @Header(name = MISSING_WORKITEM_ATTACHMENTS_COUNT,
                                            description = "Unavailable work item attachments count",
                                            schema = @Schema(implementation = String.class)
                                    ),
                                    @Header(name = WORKITEM_IDS_WITH_MISSING_ATTACHMENT,
                                            description = "Work items contained unavailable attachments",
                                            schema = @Schema(implementation = String.class)
                                    )

                            }
                    ),
                    @ApiResponse(responseCode = "204",
                            description = "Conversion job is still in progress"
                    ),
                    @ApiResponse(responseCode = "409",
                            description = "Conversion job is failed, cancelled or result is unreachable"
                    ),
                    @ApiResponse(responseCode = "404",
                            description = "Conversion job id is unknown"
                    )
            })
    public Response getPdfConverterJobResult(@PathParam("id") String jobId) {
        Optional<byte[]> pdfContent = pdfConverterJobService.getJobResult(jobId);
        if (pdfContent.isEmpty()) {
            return Response.status(HttpStatus.NO_CONTENT.value()).build();
        }
        ExportParams exportParams = pdfConverterJobService.getJobParams(jobId);
        List<String> workItemIDsWithMissingAttachment = pdfConverterJobService.getJobContext(jobId).workItemIDsWithMissingAttachment();
        String fileName = getFileName(exportParams);

        Response.ResponseBuilder responseBuilder = Response.ok(pdfContent.get())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .header(EXPORT_FILENAME_HEADER, fileName);

        if (!workItemIDsWithMissingAttachment.isEmpty()) {
            responseBuilder.header(
                    MISSING_WORKITEM_ATTACHMENTS_COUNT,
                    workItemIDsWithMissingAttachment.size()
            );
            responseBuilder.header(
                    WORKITEM_IDS_WITH_MISSING_ATTACHMENT,
                    workItemIDsWithMissingAttachment
            );
        }
        return responseBuilder.build();
    }

    @GET
    @Path("/convert/jobs")
    @Produces("application/json")
    @Operation(summary = "Returns all active DOCX conversion jobs statuses",
            responses = {
                    @ApiResponse(responseCode = "200",
                            description = "Conversion jobs statuses",
                            content = {@Content(mediaType = "application/json", schema = @Schema(implementation = Map.class))}
                    )
            })
    public Response getAllPdfConverterJobs() {
        Map<String, JobState> jobsStates = pdfConverterJobService.getAllJobsStates();
        Map<String, ConverterJobDetails> jobsDetails = jobsStates.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry ->
                        ConverterJobDetails.builder()
                                .status(convertToJobStatus(entry.getValue()))
                                .errorMessage(entry.getValue().errorMessage())
                                .build()));
        return Response.ok(jobsDetails).build();
    }

    @POST
    @Path("/convert/html")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    @Operation(summary = "Converts input HTML to DOCX",
            responses = {
                    @ApiResponse(responseCode = "200",
                            description = "Content of DOCX document as a byte array",
                            content = {@Content(mediaType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document")},
                            headers = {
                                    @Header(name = HttpHeaders.CONTENT_DISPOSITION,
                                            description = "To inform a browser that the response is a downloadable attachment",
                                            schema = @Schema(implementation = String.class)
                                    ),
                                    @Header(name = EXPORT_FILENAME_HEADER,
                                            description = "File name for converted DOCX document",
                                            schema = @Schema(implementation = String.class)
                                    )
                            }
                    )
            })
    public Response convertHtmlToPdf(
            @Parameter(description = "Required html file", required = true, schema = @Schema(type = "string", format = "binary"))
            @FormDataParam("html")
            FormDataBodyPart html,

            @Parameter(description = "Optional template file", schema = @Schema(type = "string", format = "binary"))
            @FormDataParam(value = "template")
            FormDataBodyPart template,
            @Parameter(description = "default value: document.docx") @QueryParam("fileName") String fileName,

            @Parameter(description = "Optional list of additional options", schema = @Schema(type = "array", implementation = List.class))
            @FormDataParam(value = "options")
            FormDataBodyPart options,

            @Parameter(description = "Optional params json object", schema = @Schema(type = "string"))
            @FormDataParam(value = "params")
            FormDataBodyPart params) {
        try {
            byte[] htmlBytes = html.getEntityAs(InputStream.class).readAllBytes();
            byte[] templateBytes = template != null ? template.getEntityAs(InputStream.class).readAllBytes() : null;
            List<String> optionsList = options != null
                    ? Stream.of(options.getValue().split(" ")).map(String::trim).filter(s -> !s.isEmpty()).toList()
                    : null;

            String paramsJson = params == null ? null : params.getValue();
            PandocParams pandocParams = StringUtils.isEmpty(paramsJson) ? PandocParams.builder().build() : PandocParams.fromJson(paramsJson);
            byte[] docxBytes = htmlToDocxConverter.convert(new String(htmlBytes, StandardCharsets.UTF_8), templateBytes, optionsList, pandocParams);

            String headerFileName = (fileName != null) ? fileName : "document.docx";
            return Response.ok(docxBytes)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + headerFileName)
                    .header(EXPORT_FILENAME_HEADER, headerFileName)
                    .build();
        } catch (IOException e) {
            throw new BadRequestException("Error processing files", e);
        }
    }

    @POST
    @Path("/checknestedlists")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Checks whether document contains nested lists",
            requestBody = @RequestBody(
                    description = "Export parameters used to locate and check the document for nested lists",
                    required = true,
                    content = @Content(schema = @Schema(implementation = ExportParams.class)
                    )
            ),
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Check completed successfully, returning whether nested lists are present",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = NestedListsCheck.class))
                    )
            }
    )
    @SuppressWarnings("java:S1166")
    public NestedListsCheck checkNestedLists(ExportParams exportParams) {
        DocumentData<IModule> documentData = DocumentDataFactory.getDocumentData(exportParams, true);
        @NotNull String content = Objects.requireNonNull(documentData.getContent());
        boolean containsNestedLists = new NumberedListsSanitizer().containsNestedNumberedLists(content);

        return NestedListsCheck.builder().containsNestedLists(containsNestedLists).build();
    }

    @GET
    @Path("/template")
    @Produces("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    @Operation(summary = "Returns DOCX template file",
            responses = {
                    @ApiResponse(responseCode = "200",
                            description = "DOCX template file",
                            content = {@Content(mediaType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document")},
                            headers = {
                                    @Header(name = HttpHeaders.CONTENT_DISPOSITION,
                                            description = "To inform a browser that the response is a downloadable attachment",
                                            schema = @Schema(implementation = String.class)
                                    )
                            }
                    )
            })
    public Response getTemplate() {
        Response.ResponseBuilder responseBuilder = Response.ok(docxConverter.getTemplate())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Template.docx\"");
        return responseBuilder.build();
    }

    private void validateExportParameters(ExportParams exportParams) {
        if (exportParams == null) {
            throw new BadRequestException("Missing export parameters");
        }
        if (exportParams.getProjectId() == null) {
            throw new BadRequestException("Parameter 'projectId' should be provided");
        }
        if (exportParams.getLocationPath() == null) {
            throw new BadRequestException("Parameter 'locationPath' should be provided");
        }
    }

    private String getFileName(@Nullable ExportParams exportParams) {
        if (exportParams != null) {
            return StringUtils.isEmpty(exportParams.getFileName())
                    ? new DocumentFileNameHelper().getDocumentFileName(exportParams)
                    : exportParams.getFileName();
        } else {
            return "document.docx";
        }
    }

    private ConverterJobStatus convertToJobStatus(JobState jobState) {
        if (!jobState.isDone()) {
            return ConverterJobStatus.IN_PROGRESS;
        } else if (!jobState.isCancelled() && !jobState.isCompletedExceptionally()) {
            return ConverterJobStatus.SUCCESSFULLY_FINISHED;
        } else if (jobState.isCancelled()) {
            return ConverterJobStatus.CANCELLED;
        } else {
            return ConverterJobStatus.FAILED;
        }
    }
}
