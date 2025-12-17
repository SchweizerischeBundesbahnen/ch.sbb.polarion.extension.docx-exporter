package ch.sbb.polarion.extension.docx_exporter.pandoc.service;

import ch.sbb.polarion.extension.docx_exporter.pandoc.service.model.PandocInfo;
import ch.sbb.polarion.extension.docx_exporter.pandoc.service.model.PandocParams;
import ch.sbb.polarion.extension.docx_exporter.properties.DocxExporterExtensionConfiguration;
import ch.sbb.polarion.extension.docx_exporter.util.DocxGenerationLog;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polarion.core.util.exceptions.UserFriendlyRuntimeException;
import com.polarion.core.util.logging.Logger;
import lombok.Getter;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.media.multipart.file.FileDataBodyPart;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.polarion.core.util.StringUtils.isEmpty;

@Getter
public class PandocServiceConnector {

    public static final String MEDIA_TYPE_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    public static final String MEDIA_TYPE_PDF = "application/pdf";

    private static final Logger logger = Logger.getLogger(PandocServiceConnector.class);

    private static final String PYTHON_VERSION_HEADER = "Python-Version";
    private static final String PANDOC_VERSION_HEADER = "Pandoc-Version";
    private static final String PANDOC_SERVICE_VERSION_HEADER = "Pandoc-Service-Version";

    private static final AtomicReference<String> pythonVersion = new AtomicReference<>();
    private static final AtomicReference<String> pandocVersion = new AtomicReference<>();
    private static final AtomicReference<String> pandocServiceVersion = new AtomicReference<>();

    private final @NotNull String pandocServiceBaseUrl;

    public PandocServiceConnector() {
        this(DocxExporterExtensionConfiguration.getInstance().getPandocService());
    }

    public PandocServiceConnector(@NotNull String pandocServiceBaseUrl) {
        this.pandocServiceBaseUrl = pandocServiceBaseUrl;
    }

    public byte[] convertToDocx(String htmlPage, byte[] template, List<String> options, PandocParams params) {
        return convertToDocx(htmlPage, template, options, params, null);
    }

    public byte[] convertToDocx(String htmlPage, byte[] template, List<String> options, PandocParams params, @Nullable DocxGenerationLog generationLog) {
        Client client = null;
        try {
            client = ClientBuilder.newClient();
            WebTarget webTarget = client.target(getPandocServiceBaseUrl() + "/convert/html/to/docx-with-template%s".formatted(params.toUrlParams())).register(MultiPartFeature.class);

            try (FormDataMultiPart multipart = new FormDataMultiPart()) {

                multipart.bodyPart(new FormDataBodyPart("source", htmlPage.getBytes(StandardCharsets.UTF_8), MediaType.APPLICATION_OCTET_STREAM_TYPE));
                if (template != null) {
                    multipart.bodyPart(new FormDataBodyPart(
                            FormDataContentDisposition.name("template")
                                    .fileName("template.docx")
                                    .size(template.length)
                                    .build(),
                            new ByteArrayInputStream(template),
                            MediaType.valueOf(MEDIA_TYPE_DOCX)
                    ));
                }

                if (options != null && !options.isEmpty()) {
                    String optionsString = String.join(" ", options);
                    multipart.bodyPart(new FormDataBodyPart("options", optionsString, MediaType.TEXT_PLAIN_TYPE));
                }

                Invocation.Builder requestBuilder = webTarget.request(MEDIA_TYPE_DOCX);

                long startTime = System.currentTimeMillis();
                try (Response response = requestBuilder.post(Entity.entity(multipart, multipart.getMediaType()))) {
                    if (response.getStatus() == Response.Status.OK.getStatusCode()) {
                        InputStream inputStream = response.readEntity(InputStream.class);
                        try {
                            logPandocVersionFromHeader(response);
                            byte[] docxBytes = inputStream.readAllBytes();
                            recordTiming(generationLog, "Pandoc service conversion", System.currentTimeMillis() - startTime,
                                    String.format("html_size=%d bytes, docx_size=%d bytes, template=%s",
                                            htmlPage.length(), docxBytes.length, template != null ? "yes" : "no"));
                            return docxBytes;
                        } catch (IOException e) {
                            throw new IllegalStateException("Could not read response stream", e);
                        }
                    } else {
                        String errorMessage = response.readEntity(String.class);
                        throw new IllegalStateException(String.format("Not expected response from Pandoc Service. Status: %s, Message: [%s]", response.getStatus(), errorMessage));
                    }
                }
            }
        } catch (Exception e) {
            throw new UserFriendlyRuntimeException("Could not get response from pandoc service", e);
        } finally {
            if (client != null) {
                client.close();
            }
        }
    }

    private void recordTiming(@Nullable DocxGenerationLog generationLog, String stageName, long durationMs, String details) {
        if (generationLog != null) {
            generationLog.recordTiming(stageName, durationMs, details);
        }
    }

    public byte[] convertToPDF(File docx) {
        Client client = null;
        try {
            client = ClientBuilder.newClient();
            WebTarget webTarget = client.target(getPandocServiceBaseUrl() + "/convert/docx/to/pdf").register(MultiPartFeature.class);

            try (FormDataMultiPart multipart = new FormDataMultiPart()) {
                multipart.bodyPart(new FileDataBodyPart("source", docx, MediaType.APPLICATION_OCTET_STREAM_TYPE));

                Invocation.Builder requestBuilder = webTarget.request(MEDIA_TYPE_PDF);

                try (Response response = requestBuilder.post(Entity.entity(multipart, multipart.getMediaType()))) {
                    if (response.getStatus() == Response.Status.OK.getStatusCode()) {
                        InputStream inputStream = response.readEntity(InputStream.class);
                        try {
                            logPandocVersionFromHeader(response);
                            return inputStream.readAllBytes();
                        } catch (IOException e) {
                            throw new IllegalStateException("Could not read response stream", e);
                        }
                    } else {
                        String errorMessage = response.readEntity(String.class);
                        throw new IllegalStateException(String.format("Not expected response from Pandoc Service. Status: %s, Message: [%s]", response.getStatus(), errorMessage));
                    }
                }
            }
        } catch (Exception e) {
            throw new UserFriendlyRuntimeException("Could not get response from pandoc service", e);
        } finally {
            if (client != null) {
                client.close();
            }
        }
    }

    public byte[] getTemplate() {
        Client client = ClientBuilder.newClient();
        WebTarget webTarget = client.target(getPandocServiceBaseUrl() + "/docx-template");

        try (Response response = webTarget.request(MEDIA_TYPE_DOCX).get()) {
            if (response.getStatus() == Response.Status.OK.getStatusCode()) {

                try (InputStream inputStream = response.readEntity(InputStream.class);
                     ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

                    byte[] buffer = new byte[4096]; // Adjust buffer size as needed
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                    return outputStream.toByteArray();

                } catch (IOException e) {
                    throw new IllegalStateException("Could not read response from Pandoc Service");
                }
            } else {
                throw new IllegalStateException("Could not get proper response from Pandoc Service");
            }
        } finally {
            client.close();
        }
    }

    public PandocInfo getPandocInfo() {
        Client client = null;
        try {
            client = ClientBuilder.newClient();
            WebTarget webTarget = client.target(getPandocServiceBaseUrl() + "/version");

            try (Response response = webTarget.request(MediaType.TEXT_PLAIN).get()) {
                if (response.getStatus() == Response.Status.OK.getStatusCode()) {
                    String responseContent = response.readEntity(String.class);

                    try {
                        return new ObjectMapper().readValue(responseContent, PandocInfo.class);
                    } catch (JsonProcessingException e) {
                        throw new IllegalStateException("Could not parse response", e);
                    }
                } else {
                    throw new IllegalStateException("Could not get proper response from Pandoc Service");
                }
            }
        } finally {
            if (client != null) {
                client.close();
            }
        }
    }

    private void logPandocVersionFromHeader(Response response) {
        String actualPythonVersion = response.getHeaderString(PYTHON_VERSION_HEADER);
        String actualPandocVersion = response.getHeaderString(PANDOC_VERSION_HEADER);
        String actualPandocServiceVersion = response.getHeaderString(PANDOC_SERVICE_VERSION_HEADER);

        boolean hasPythonVersionChanged = hasVersionChanged(actualPythonVersion, pythonVersion);
        boolean hasPandocVersionChanged = hasVersionChanged(actualPandocVersion, pandocVersion);
        boolean hasPandocServiceVersionChanged = hasVersionChanged(actualPandocServiceVersion, pandocServiceVersion);

        if (hasPandocVersionChanged || hasPythonVersionChanged || hasPandocServiceVersionChanged) {
            logger.info(String.format("PandocService started from Docker image version '%s' uses Pandoc version '%s' and Python version '%s'", actualPandocServiceVersion, actualPandocVersion, actualPythonVersion));
        }
    }

    public boolean hasVersionChanged(String actualVersion, AtomicReference<String> version) {
        return !isEmpty(actualVersion) && !actualVersion.equals(version.getAndSet(actualVersion));
    }
}
