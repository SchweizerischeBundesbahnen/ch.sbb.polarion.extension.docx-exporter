package ch.sbb.polarion.extension.docx_exporter.converter;

import ch.sbb.polarion.extension.docx_exporter.configuration.DocxExporterExtensionConfigurationExtension;
import ch.sbb.polarion.extension.docx_exporter.pandoc.service.PandocServiceConnector;
import ch.sbb.polarion.extension.docx_exporter.pandoc.service.model.PandocParams;
import ch.sbb.polarion.extension.docx_exporter.util.HtmlProcessor;
import ch.sbb.polarion.extension.docx_exporter.util.DocxTemplateProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, DocxExporterExtensionConfigurationExtension.class})
class HtmlToDocxConverterTest {
    @Mock
    private DocxTemplateProcessor docxTemplateProcessor;

    @Mock
    private HtmlProcessor htmlProcessor;

    @Mock
    private PandocServiceConnector serviceConnector;

    @InjectMocks
    private HtmlToDocxConverter htmlToDocxConverter;

    @Test
    void shouldCallConnector() {
        String html = """
                <html>
                    <body>
                        <span>example text</span>
                    </body>
                </html>""";
        PandocParams params = PandocParams.builder().build();
        when(htmlProcessor.internalizeLinks(anyString())).thenReturn("<html><body>internalized</body></html>");
        when(htmlProcessor.replaceResourcesAsBase64Encoded("<html><body>internalized</body></html>"))
                .thenReturn("<html><body>inlined</body></html>");
        HtmlToDocxConverter converter = new HtmlToDocxConverter(htmlProcessor, serviceConnector);
        assertDoesNotThrow(() -> converter.convert(html, null, params));
        // the service converts what was processed here, never the html as it arrived: the original
        // names the addresses of a document, and nothing vetted them
        verify(serviceConnector).convertToDocx("<html><body>inlined</body></html>", null, params);
    }

    @Test
    void shouldThrowIllegalArgumentForMalformedHtml() {
        String html = "<span>example text</span>";
        PandocParams params = PandocParams.builder().build();

        assertThatThrownBy(() -> htmlToDocxConverter.convert(html, null, params))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("html is malformed");
    }

}
