package ch.sbb.polarion.extension.docx_exporter.pandoc.service;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PandocServiceConnectorTest {

    private final PandocServiceConnector connector = new PandocServiceConnector("http://localhost:9082");

    @Test
    void readResponseBytesReturnsStreamContent() {
        byte[] content = {1, 2, 3};
        Response response = mock(Response.class);
        when(response.readEntity(InputStream.class)).thenReturn(new java.io.ByteArrayInputStream(content));

        assertArrayEquals(content, connector.readResponseBytes(response));
    }

    @Test
    void readResponseBytesWrapsIoException() {
        Response response = mock(Response.class);
        when(response.readEntity(InputStream.class)).thenReturn(new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("stream is broken");
            }
        });

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> connector.readResponseBytes(response));
        assertEquals("Could not read response stream", exception.getMessage());
    }
}
