package ch.sbb.polarion.extension.docx_exporter.converter;

import ch.sbb.polarion.extension.docx_exporter.pandoc.service.PandocServiceConnector;
import ch.sbb.polarion.extension.docx_exporter.pandoc.service.model.PandocParams;
import ch.sbb.polarion.extension.docx_exporter.properties.DocxExporterExtensionConfiguration;
import ch.sbb.polarion.extension.docx_exporter.rest.model.conversion.ExportParams;
import ch.sbb.polarion.extension.docx_exporter.rest.model.documents.DocumentData;
import ch.sbb.polarion.extension.docx_exporter.rest.model.settings.templates.TemplatesModel;
import ch.sbb.polarion.extension.docx_exporter.rest.model.settings.webhooks.AuthType;
import ch.sbb.polarion.extension.docx_exporter.rest.model.settings.webhooks.WebhookConfig;
import ch.sbb.polarion.extension.docx_exporter.rest.model.settings.webhooks.WebhooksModel;
import ch.sbb.polarion.extension.docx_exporter.service.DocxExporterPolarionService;
import ch.sbb.polarion.extension.docx_exporter.settings.LocalizationSettings;
import ch.sbb.polarion.extension.docx_exporter.settings.TemplatesSettings;
import ch.sbb.polarion.extension.docx_exporter.settings.WebhooksSettings;
import ch.sbb.polarion.extension.docx_exporter.util.DebugDataStorage;
import ch.sbb.polarion.extension.docx_exporter.util.DocumentDataFactory;
import ch.sbb.polarion.extension.docx_exporter.util.DocxExporterFileResourceProvider;
import ch.sbb.polarion.extension.docx_exporter.util.DocxGenerationLog;
import ch.sbb.polarion.extension.docx_exporter.util.DocxTemplateProcessor;
import ch.sbb.polarion.extension.docx_exporter.util.EnumValuesProvider;
import ch.sbb.polarion.extension.docx_exporter.util.HtmlLogger;
import ch.sbb.polarion.extension.docx_exporter.util.HtmlProcessor;
import ch.sbb.polarion.extension.docx_exporter.util.html.HtmlLinksHelper;
import ch.sbb.polarion.extension.generic.settings.SettingId;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polarion.alm.projects.model.IUniqueObject;
import com.polarion.alm.tracker.model.ITrackerProject;
import com.polarion.core.util.StringUtils;
import com.polarion.core.util.logging.Logger;
import com.polarion.platform.internal.security.UserAccountVault;
import lombok.AllArgsConstructor;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

@AllArgsConstructor
@SuppressWarnings("java:S1200")
public class DocxConverter {
    private final Logger logger = Logger.getLogger(DocxConverter.class);
    private final DocxExporterPolarionService docxExporterPolarionService;

    private final PandocServiceConnector pandocServiceConnector;
    private final HtmlProcessor htmlProcessor;
    private final DocxTemplateProcessor docxTemplateProcessor;

    public DocxConverter() {
        docxExporterPolarionService = new DocxExporterPolarionService();
        pandocServiceConnector = new PandocServiceConnector();
        DocxExporterFileResourceProvider fileResourceProvider = new DocxExporterFileResourceProvider();
        htmlProcessor = new HtmlProcessor(fileResourceProvider, new LocalizationSettings(), new HtmlLinksHelper(fileResourceProvider));
        docxTemplateProcessor = new DocxTemplateProcessor();
    }

    public byte[] convertToDocx(@NotNull ExportParams exportParams) {
        DocxGenerationLog generationLog = new DocxGenerationLog();
        generationLog.log("Starting DOCX generation");

        try {
            // Get tracker project and document data
            @Nullable ITrackerProject project = getTrackerProject(exportParams);
            @NotNull final DocumentData<? extends IUniqueObject> documentData = DocumentDataFactory.getDocumentData(exportParams, true);

            // Prepare HTML content
            @NotNull String htmlContent = generationLog.timed("Prepare HTML content",
                    () -> prepareHtmlContent(exportParams, project, documentData, generationLog),
                    html -> String.format("html_size=%d bytes", html.length()));

            // Set HTML size metric
            generationLog.setHtmlSize(htmlContent.length());

            generationLog.log("HTML is ready, starting DOCX generation");

            // Process template if specified
            byte[] template = null;
            if (exportParams.getTemplate() != null) {
                template = generationLog.timed("Process template", () -> {
                    TemplatesModel templatesModel = new TemplatesSettings().load(exportParams.getProjectId(), SettingId.fromName(exportParams.getTemplate()));
                    if (templatesModel.getTemplate() != null) {
                        return docxTemplateProcessor.processDocxTemplate(templatesModel.getTemplate(), documentData, exportParams);
                    }
                    return null;
                });
            }

            // Generate DOCX
            final byte[] finalTemplate = template;
            byte[] bytes = generationLog.timed("Pandoc conversion",
                    () -> generateDocx(htmlContent, finalTemplate, PandocParams.builder().orientation(exportParams.getOrientation()).paperSize(exportParams.getPaperSize()).build()),
                    docx -> String.format("docx_size=%d bytes", docx.length));

            // Set DOCX metrics
            generationLog.setDocxMetrics(bytes.length, 0);

            // Finalize timing
            generationLog.finish();

            // Log debug information if enabled
            if (exportParams.getInternalContent() == null) { //do not log time for internal parts processing
                String finalMessage = "DOCX document '" + documentData.getTitle() + "' has been generated within " + generationLog.getTotalDurationMs() + " milliseconds";
                logger.info(finalMessage);
                generationLog.log(finalMessage);

                if (DocxExporterExtensionConfiguration.getInstance().isDebug()) {
                    String timingReport = generationLog.generateTimingReport(documentData.getTitle());
                    // Save to file system
                    new HtmlLogger().log(documentData.getContent(), htmlContent, timingReport);
                    // Also save to storage for REST API access
                    saveDebugDataToStorage(documentData.getContent(), htmlContent, timingReport, documentData.getTitle());
                }
            }

            return bytes;
        } catch (Exception e) {
            generationLog.finish();
            generationLog.log("DOCX generation failed: " + e.getMessage());
            if (DocxExporterExtensionConfiguration.getInstance().isDebug()) {
                String timingReport = generationLog.generateTimingReport("FAILED");
                new HtmlLogger().log("", "", timingReport);
                saveDebugDataToStorage("", "", timingReport, "FAILED");
            }
            throw e;
        }
    }

    public @NotNull String prepareHtmlContent(@NotNull ExportParams exportParams) {
        @Nullable ITrackerProject project = getTrackerProject(exportParams);
        @NotNull final DocumentData<? extends IUniqueObject> documentData = DocumentDataFactory.getDocumentData(exportParams, true);
        return prepareHtmlContent(exportParams, project, documentData, null);
    }

    private @Nullable ITrackerProject getTrackerProject(@NotNull ExportParams exportParams) {
        ITrackerProject project = null;
        if (!StringUtils.isEmpty(exportParams.getProjectId())) {
            project = docxExporterPolarionService.getTrackerProject(exportParams.getProjectId());
        }
        return project;
    }

    private @NotNull String prepareHtmlContent(@NotNull ExportParams exportParams, @Nullable ITrackerProject project, @NotNull DocumentData<? extends IUniqueObject> documentData, @Nullable DocxGenerationLog generationLog) {
        String preparedDocumentContent = postProcessDocumentContent(exportParams, project, documentData.getContent(), generationLog);
        String composedHtml = timedIfNotNull(generationLog, "Compose HTML", () -> composeHtml(documentData.getTitle(), preparedDocumentContent));
        String internalizedHtml = timedIfNotNull(generationLog, "Internalize links", () -> htmlProcessor.internalizeLinks(composedHtml));
        return timedIfNotNull(generationLog, "Apply webhooks", () -> applyWebhooks(exportParams, internalizedHtml));
    }

    private <T> T timedIfNotNull(@Nullable DocxGenerationLog generationLog, String stageName, java.util.function.Supplier<T> supplier) {
        if (generationLog != null) {
            return generationLog.timed(stageName, supplier);
        }
        return supplier.get();
    }

    private @NotNull String applyWebhooks(@NotNull ExportParams exportParams, @NotNull String htmlContent) {
        // Skip webhooks processing among other if this functionality is not enabled by system administrator
        if (!DocxExporterExtensionConfiguration.getInstance().getWebhooksEnabled() || exportParams.getWebhooks() == null) {
            return htmlContent;
        }

        WebhooksModel webhooksModel = new WebhooksSettings().load(exportParams.getProjectId(), SettingId.fromName(exportParams.getWebhooks()));
        String result = htmlContent;
        for (WebhookConfig webhookConfig : webhooksModel.getWebhookConfigs()) {
            result = applyWebhook(webhookConfig, exportParams, result);
        }
        return result;
    }

    public byte[] getTemplate() {
        return pandocServiceConnector.getTemplate();
    }

    private @NotNull String applyWebhook(@NotNull WebhookConfig webhookConfig, @NotNull ExportParams exportParams, @NotNull String htmlContent) {
        Client client = null;
        try {
            client = ClientBuilder.newClient();
            WebTarget webTarget = client.target(webhookConfig.getUrl()).register(MultiPartFeature.class);

            try (FormDataMultiPart multipart = new FormDataMultiPart()) {

                multipart.bodyPart(new FormDataBodyPart("exportParams", new ObjectMapper().writeValueAsString(exportParams), MediaType.APPLICATION_JSON_TYPE));
                multipart.bodyPart(new FormDataBodyPart("html", htmlContent.getBytes(StandardCharsets.UTF_8), MediaType.APPLICATION_OCTET_STREAM_TYPE));

                Invocation.Builder requestBuilder = webTarget.request(MediaType.TEXT_PLAIN);

                addAuthHeader(webhookConfig, requestBuilder);

                try (Response response = requestBuilder.post(Entity.entity(multipart, multipart.getMediaType()))) {
                    if (response.getStatus() == Response.Status.OK.getStatusCode()) {
                        try (InputStream inputStream = response.readEntity(InputStream.class)) {
                            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                        }
                    } else {
                        logger.error(String.format("Could not get proper response from webhook [%s]: response status %s", webhookConfig.getUrl(), response.getStatus()));
                    }
                }
            }
        } catch (Exception e) {
            logger.error(String.format("Could not get response from webhook [%s]", webhookConfig.getUrl()), e);
        } finally {
            if (client != null) {
                client.close();
            }
        }

        // In case of errors return initial HTML without modification
        return htmlContent;
    }

    private static void addAuthHeader(@NotNull WebhookConfig webhookConfig, @NotNull Invocation.Builder requestBuilder) {
        if (webhookConfig.getAuthType() == null || webhookConfig.getAuthTokenName() == null) {
            return;
        }

        String authInfoFromUserAccountVault = getAuthInfoFromUserAccountVault(webhookConfig.getAuthType(), webhookConfig.getAuthTokenName());
        if (authInfoFromUserAccountVault == null) {
            return;
        }

        requestBuilder.header(HttpHeaders.AUTHORIZATION, webhookConfig.getAuthType().getAuthHeaderPrefix() + " " + authInfoFromUserAccountVault);
    }

    private static @Nullable String getAuthInfoFromUserAccountVault(@NotNull AuthType authType, @NotNull String authTokenName) {
        @NotNull UserAccountVault.Credentials credentials = UserAccountVault.getInstance().getCredentialsForKey(authTokenName);

        return switch (authType) {
            case BASIC_AUTH -> {
                String authInfo = credentials.getUser() + ":" + credentials.getPassword();
                yield Base64.getEncoder().encodeToString(authInfo.getBytes());
            }
            case BEARER_TOKEN -> credentials.getPassword();
        };
    }

    @VisibleForTesting
    byte[] generateDocx(String htmlPage, byte[] template, @NotNull PandocParams params) {
        int headerIndex = htmlPage.indexOf("<div class='header'>");
        if (headerIndex > -1) {
            int contentIndex = htmlPage.indexOf("<div class='content'>");
            htmlPage = htmlPage.substring(0, headerIndex) + htmlPage.substring(contentIndex);
        }
        htmlPage = htmlPage.replace("<div style='break-after:page'>page to be removed</div><?xml version='1.0' encoding='UTF-8'?>", "");
        return pandocServiceConnector.convertToDocx(htmlPage, template, params);
    }

    @VisibleForTesting
    String postProcessDocumentContent(@NotNull ExportParams exportParams, @Nullable ITrackerProject project, @Nullable String documentContent) {
        return postProcessDocumentContent(exportParams, project, documentContent, null);
    }

    String postProcessDocumentContent(@NotNull ExportParams exportParams, @Nullable ITrackerProject project, @Nullable String documentContent, @Nullable DocxGenerationLog generationLog) {
        if (documentContent != null) {
            List<String> selectedRoleEnumValues = project == null ? Collections.emptyList() : EnumValuesProvider.getBidirectionalLinkRoleNames(project, exportParams.getLinkedWorkitemRoles());
            return htmlProcessor.processHtmlForExport(documentContent, exportParams, selectedRoleEnumValues, generationLog);
        } else {
            return "";
        }
    }

    @NotNull
    @VisibleForTesting
    String composeHtml(@NotNull String documentName, String documentContent) {
        String content = "<div class='content'>" + documentContent + "</div>";
        return docxTemplateProcessor.processUsing(documentName, content);
    }

    private void saveDebugDataToStorage(@Nullable String originalHtml,
                                        @Nullable String processedHtml,
                                        @Nullable String timingReport,
                                        @Nullable String documentTitle) {
        String jobId = DebugDataStorage.getCurrentJobId();
        if (jobId == null) {
            // Not running as async job, skip storage
            return;
        }

        String currentUser = docxExporterPolarionService.getSecurityService().getCurrentUser();
        if (currentUser == null) {
            currentUser = "unknown";
        }

        DebugDataStorage.storeForCurrentJob(originalHtml, processedHtml, timingReport, currentUser, documentTitle);
    }
}
