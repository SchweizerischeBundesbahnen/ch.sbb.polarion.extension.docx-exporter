package ch.sbb.polarion.extension.docx_exporter.rest.controller;

import ch.sbb.polarion.extension.docx_exporter.converter.DocxConverter;
import ch.sbb.polarion.extension.docx_exporter.converter.DocxConverterJobsService;
import ch.sbb.polarion.extension.docx_exporter.converter.HtmlToDocxConverter;
import ch.sbb.polarion.extension.docx_exporter.rest.filter.RolesRestricted;
import ch.sbb.polarion.extension.docx_exporter.rest.model.conversion.ExportParams;
import ch.sbb.polarion.extension.docx_exporter.service.DocxExporterPolarionService;
import ch.sbb.polarion.extension.generic.rest.filter.Secured;
import ch.sbb.polarion.extension.generic.util.RequestContextUtil;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.jetbrains.annotations.VisibleForTesting;

import jakarta.inject.Singleton;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

@Singleton
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
        super(docxConverter, pdfConverterJobService, uriInfo, htmlToDocxConverter, docxExporterPolarionService);
        this.polarionService = docxExporterPolarionService;
    }

    @Override
    @RolesRestricted
    public Response convertToDocx(ExportParams exportParams) {
        return polarionService.callPrivileged(() -> super.convertToDocx(exportParams));
    }

    @Override
    @RolesRestricted
    public String prepareHtmlContent(ExportParams exportParams) {
        return polarionService.callPrivileged(() -> super.prepareHtmlContent(exportParams));
    }

    @Override
    @RolesRestricted
    public Response startPdfConverterJob(ExportParams exportParams) {
        // The job runs after this response and ends the session itself; a start which fails gives it back.
        RequestContextUtil.keepSessionAlive();
        try {
            return polarionService.callPrivileged(() -> super.startPdfConverterJob(exportParams));
        } catch (RuntimeException e) {
            RequestContextUtil.releaseSession();
            throw e;
        }
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
    public Response getExportPermission(String projectId) {
        return polarionService.callPrivileged(() -> super.getExportPermission(projectId));
    }


}
