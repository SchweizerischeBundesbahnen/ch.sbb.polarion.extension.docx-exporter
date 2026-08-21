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
import org.jetbrains.annotations.VisibleForTesting;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
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
    private static final String API_KEY_HEADER = "X-API-Key";

    private static final AtomicReference<String> pythonVersion = new AtomicReference<>();
    private static final AtomicReference<String> pandocVersion = new AtomicReference<>();
    private static final AtomicReference<String> pandocServiceVersion = new AtomicReference<>();

    private final @NotNull String pandocServiceBaseUrl;

    private final @NotNull ApiKeyProvider apiKeyProvider;

    public PandocServiceConnector() {
        this(DocxExporterExtensionConfiguration.getInstance().getPandocService());
    }

    public PandocServiceConnector(@NotNull String pandocServiceBaseUrl) {
        this(pandocServiceBaseUrl, new ApiKeyProvider());
    }

    public PandocServiceConnector(@NotNull String pandocServiceBaseUrl, @NotNull ApiKeyProvider apiKeyProvider) {
        this.pandocServiceBaseUrl = pandocServiceBaseUrl;
        this.apiKeyProvider = apiKeyProvider;
    }

    /**
     * Builds the request, carrying the API key when one is configured.
     * <p>
     * A key is a reusable credential, so it is only ever handed to a transport which protects it.
     * Where the service is named over plain http the request is refused instead: sending the key
     * would put it on the wire for anyone on the path to keep.
     */
    @VisibleForTesting
    Invocation.Builder requestWithApiKey(@NotNull WebTarget webTarget, @NotNull String acceptType, @Nullable String apiKey) {
        Invocation.Builder builder = webTarget.request(acceptType);
        if (apiKey == null) {
            return builder;
        }
        failOnInsecureTransport();
        return builder.header(API_KEY_HEADER, apiKey);
    }

    /**
     * Refuses to send the key where the transport does not protect it.
     */
    @VisibleForTesting
    void failOnInsecureTransport() {
        if (!getPandocServiceBaseUrl().toLowerCase(Locale.ROOT).startsWith("https://")) {
            throw new UserFriendlyRuntimeException(String.format(
                    "The pandoc API key is not sent over plain http. Name the service in '%s' with an https address, or clear '%s' where the service needs no key.",
                    DocxExporterExtensionConfiguration.PANDOC_SERVICE, DocxExporterExtensionConfiguration.PANDOC_API_KEY_SECRET));
        }
    }

    /**
     * Tells the two ways a 401 is reached apart, since each one has a different fix.
     */
    @VisibleForTesting
    static @NotNull String unauthorizedMessage(boolean apiKeySent) {
        return apiKeySent
                ? "Pandoc Service rejected the configured API key. Check that the Polarion secret named in '" + DocxExporterExtensionConfiguration.PANDOC_API_KEY_SECRET + "' holds the key the service was started with."
                : "Pandoc Service requires an API key, none is configured. Name the Polarion secret holding it in '" + DocxExporterExtensionConfiguration.PANDOC_API_KEY_SECRET + "'.";
    }

    @VisibleForTesting
    void failOnUnauthorized(@NotNull Response response, @Nullable String apiKey) {
        if (response.getStatus() == Response.Status.UNAUTHORIZED.getStatusCode()) {
            // user friendly on purpose: the catch-all below keeps such a failure as it is, so the
            // reason reaches the export dialog rather than only the server log
            throw new UserFriendlyRuntimeException(unauthorizedMessage(apiKey != null));
        }
    }

    public byte[] convertToDocx(String htmlPage, byte[] template,PandocParams params) {
        return convertToDocx(htmlPage, template, params, null);
    }

    public byte[] convertToDocx(String htmlPage, byte[] template, PandocParams params, @Nullable DocxGenerationLog generationLog) {
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

                String apiKey = apiKeyProvider.getApiKey();
                Invocation.Builder requestBuilder = requestWithApiKey(webTarget, MEDIA_TYPE_DOCX, apiKey);

                long startTime = System.currentTimeMillis();
                try (Response response = requestBuilder.post(Entity.entity(multipart, multipart.getMediaType()))) {
                    failOnUnauthorized(response, apiKey);
                    if (response.getStatus() == Response.Status.OK.getStatusCode()) {
                        byte[] docxBytes = readResponseBytes(response);
                        recordTiming(generationLog, "Pandoc service conversion", System.currentTimeMillis() - startTime,
                                String.format("html_size=%d bytes, docx_size=%d bytes, template=%s",
                                        htmlPage.length(), docxBytes.length, template != null ? "yes" : "no"));
                        return docxBytes;
                    } else {
                        String errorMessage = response.readEntity(String.class);
                        throw new IllegalStateException(String.format("Not expected response from Pandoc Service. Status: %s, Message: [%s]", response.getStatus(), errorMessage));
                    }
                }
            }
        } catch (UserFriendlyRuntimeException e) {
            // the api key failures explain themselves and the job stores this very message
            throw e;
        } catch (Exception e) {
            throw new UserFriendlyRuntimeException("Could not get response from pandoc service", e);
        } finally {
            if (client != null) {
                client.close();
            }
        }
    }

    @VisibleForTesting
    byte[] readResponseBytes(@NotNull Response response) {
        InputStream inputStream = response.readEntity(InputStream.class);
        try {
            logPandocVersionFromHeader(response);
            return inputStream.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Could not read response stream", e);
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

                String apiKey = apiKeyProvider.getApiKey();
                Invocation.Builder requestBuilder = requestWithApiKey(webTarget, MEDIA_TYPE_PDF, apiKey);

                try (Response response = requestBuilder.post(Entity.entity(multipart, multipart.getMediaType()))) {
                    failOnUnauthorized(response, apiKey);
                    if (response.getStatus() == Response.Status.OK.getStatusCode()) {
                        return readResponseBytes(response);
                    } else {
                        String errorMessage = response.readEntity(String.class);
                        throw new IllegalStateException(String.format("Not expected response from Pandoc Service. Status: %s, Message: [%s]", response.getStatus(), errorMessage));
                    }
                }
            }
        } catch (UserFriendlyRuntimeException e) {
            // the api key failures explain themselves and the job stores this very message
            throw e;
        } catch (Exception e) {
            throw new UserFriendlyRuntimeException("Could not get response from pandoc service", e);
        } finally {
            if (client != null) {
                client.close();
            }
        }
    }

    public byte[] getTemplate() {
        // resolved first: the lookup throws on a misconfigured secret, and a client created before it
        // would never be closed on that path
        String apiKey = apiKeyProvider.getApiKey();

        Client client = ClientBuilder.newClient();
        WebTarget webTarget = client.target(getPandocServiceBaseUrl() + "/docx-template");

        try (Response response = requestWithApiKey(webTarget, MEDIA_TYPE_DOCX, apiKey).get()) {
            failOnUnauthorized(response, apiKey);
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
