package ch.sbb.polarion.extension.docx_exporter.rest.controller;

import ch.sbb.polarion.extension.docx_exporter.converter.DocxConverter;
import ch.sbb.polarion.extension.docx_exporter.converter.DocxConverterJobsService;
import ch.sbb.polarion.extension.docx_exporter.converter.HtmlToDocxConverter;
import ch.sbb.polarion.extension.docx_exporter.rest.model.NestedListsCheck;
import ch.sbb.polarion.extension.docx_exporter.rest.model.conversion.ExportParams;
import ch.sbb.polarion.extension.docx_exporter.service.DocxExporterPolarionService;
import ch.sbb.polarion.extension.generic.rest.filter.LogoutFilter;
import ch.sbb.polarion.extension.generic.rest.filter.Secured;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.jetbrains.annotations.VisibleForTesting;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

@Secured
@Path("/api")
public class ConverterApiController extends ConverterInternalController {

    private final DocxExporterPolarionService polarionService;

    public ConverterApiController() {
        this.polarionService = new DocxExporterPolarionService();
    }

    @VisibleForTesting
    @SuppressWarnings("squid:S5803")
    ConverterApiController(DocxExporterPolarionService docxExporterPolarionService, DocxConverter docxConverter, DocxConverterJobsService pdfConverterJobService, UriInfo uriInfo, HtmlToDocxConverter htmlToDocxConverter) {
        super(docxConverter, pdfConverterJobService, uriInfo, htmlToDocxConverter);
        this.polarionService = docxExporterPolarionService;
    }

    @Override
    public Response convertToDocx(ExportParams exportParams) {
        return polarionService.callPrivileged(() -> super.convertToDocx(exportParams));
    }

    @Override
    public String prepareHtmlContent(ExportParams exportParams) {
        return polarionService.callPrivileged(() -> super.prepareHtmlContent(exportParams));
    }

    @Override
    public Response startPdfConverterJob(ExportParams exportParams) {
        // In async case logout inside the filter must be deactivated. Async Job itself will care about logout after finishing
        deactivateLogoutFilter();

        return polarionService.callPrivileged(() -> super.startPdfConverterJob(exportParams));
    }

    @Override
    public Response getPdfConverterJobStatus(String jobId) {
        return polarionService.callPrivileged(() -> super.getPdfConverterJobStatus(jobId));
    }

    @Override
    public Response getPdfConverterJobResult(String jobId) {
        return polarionService.callPrivileged(() -> super.getPdfConverterJobResult(jobId));
    }

    @Override
    public Response getAllPdfConverterJobs() {
        return polarionService.callPrivileged(super::getAllPdfConverterJobs);
    }

    @Override
    public Response getTemplate() {
        return polarionService.callPrivileged(super::getTemplate);
    }

    @Override
    public Response convertHtmlToPdf(FormDataBodyPart html, FormDataBodyPart template, String fileName, FormDataBodyPart options, FormDataBodyPart params) {
        return polarionService.callPrivileged(() -> super.convertHtmlToPdf(html, template, fileName, options, params));
    }

    @Override
    public NestedListsCheck checkNestedLists(ExportParams exportParams) {
        return polarionService.callPrivileged(() -> super.checkNestedLists(exportParams));
    }

    private void deactivateLogoutFilter() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes != null) {
            requestAttributes.setAttribute(LogoutFilter.ASYNC_SKIP_LOGOUT, Boolean.TRUE, RequestAttributes.SCOPE_REQUEST);
            RequestContextHolder.setRequestAttributes(requestAttributes);
        }
    }

}
