package ch.sbb.polarion.extension.docx_exporter.util;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockConstruction;

class VersionUtilsTest {

    @Test
    @SuppressWarnings("unused")
    void testGetLatestCompatibleVersionPandocService() {
        assertEquals("1.4.0", VersionUtils.getLatestCompatibleVersionPandocService());

        try (MockedConstruction<Properties> properties = mockConstruction(Properties.class, (mock, context) -> doThrow(IOException.class).when(mock).load(any(InputStream.class)))) {
            assertNull(VersionUtils.getLatestCompatibleVersionPandocService());
        }
    }

}
