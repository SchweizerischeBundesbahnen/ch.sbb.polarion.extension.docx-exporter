package ch.sbb.polarion.extension.docx_exporter.pandoc.service;

import ch.sbb.polarion.extension.docx_exporter.properties.DocxExporterExtensionConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;

import static ch.sbb.polarion.extension.docx_exporter.pandoc.service.PandocServiceConnector.MEDIA_TYPE_DOCX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PandocServiceConnectorApiKeyTest {

    private static final String API_KEY = "s3cr3t";

    private static PandocServiceConnector connector() {
        return new PandocServiceConnector("http://localhost:9082", new ApiKeyProvider());
    }

    @Test
    void shouldSendConfiguredKeyAsApiKeyHeader() {
        WebTarget webTarget = mock(WebTarget.class);
        Invocation.Builder builder = mock(Invocation.Builder.class);
        Invocation.Builder builderWithHeader = mock(Invocation.Builder.class);
        when(webTarget.request(MEDIA_TYPE_DOCX)).thenReturn(builder);
        when(builder.header("X-API-Key", API_KEY)).thenReturn(builderWithHeader);

        assertThat(connector().requestWithApiKey(webTarget, MEDIA_TYPE_DOCX, API_KEY)).isSameAs(builderWithHeader);
    }

    @Test
    void shouldSendNoHeaderWhenNoKeyConfigured() {
        WebTarget webTarget = mock(WebTarget.class);
        Invocation.Builder builder = mock(Invocation.Builder.class);
        when(webTarget.request(MEDIA_TYPE_DOCX)).thenReturn(builder);

        assertThat(connector().requestWithApiKey(webTarget, MEDIA_TYPE_DOCX, null)).isSameAs(builder);
        verify(builder, never()).header(ArgumentMatchers.anyString(), ArgumentMatchers.any());
    }

    @Test
    void shouldTellTheTwoUnauthorizedCausesApart() {
        assertThat(PandocServiceConnector.unauthorizedMessage(true))
                .contains("rejected the configured API key")
                .contains(DocxExporterExtensionConfiguration.PANDOC_API_KEY_SECRET);

        assertThat(PandocServiceConnector.unauthorizedMessage(false))
                .contains("requires an API key, none is configured")
                .contains(DocxExporterExtensionConfiguration.PANDOC_API_KEY_SECRET);
    }
}
