package ch.sbb.polarion.extension.docx_exporter.converter;

import ch.sbb.polarion.extension.docx_exporter.configuration.DocxExporterExtensionConfigurationExtension;
import ch.sbb.polarion.extension.docx_exporter.pandoc.service.PandocServiceConnector;
import ch.sbb.polarion.extension.docx_exporter.util.HtmlProcessor;
import ch.sbb.polarion.extension.docx_exporter.util.DocxTemplateProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, DocxExporterExtensionConfigurationExtension.class})
class HtmlToDocxConverterTest {
    @Mock
    private DocxTemplateProcessor docxTemplateProcessor;

    @Mock
    private HtmlProcessor htmlProcessor;

    @Mock
    private PandocServiceConnector pandocServiceConnector;

    @InjectMocks
    private HtmlToDocxConverter htmlToDocxConverter;

    @Test
    void shouldInjectHeadAndStyle() {
        String html = """
                <html>
                    <body>
                        <span>example text</span>
                    </body>
                </html>""";

        when(docxTemplateProcessor.buildBaseUrlHeader()).thenReturn("<base href='http://test' />");
        when(docxTemplateProcessor.buildSizeCss()).thenReturn("@page {size: test;}");
        when(htmlProcessor.replaceResourcesAsBase64Encoded(anyString())).thenAnswer(invocation ->
                invocation.getArgument(0));
        when(htmlProcessor.internalizeLinks(anyString())).thenAnswer(a -> a.getArgument(0));
        String resultHtml = htmlToDocxConverter.preprocessHtml(html);

        assertThat(resultHtml).isEqualTo("""
                <html><head><base href='http://test' /><style>@page {size: test;}</style></head>
                    <body>
                        <span>example text</span>
                    </body>
                </html>""");

        verify(htmlProcessor).replaceResourcesAsBase64Encoded(resultHtml);
    }

    @Test
    void shouldExtendExistingHeadAndStyle() {
        String html = """
                <html>
                    <head>
                        test head content before
                        <style>style content</style>
                    </head>
                    <body>
                        <span>example text</span>
                    </body>
                </html>""";

        when(docxTemplateProcessor.buildBaseUrlHeader()).thenReturn("<base href='http://test' />");
        when(docxTemplateProcessor.buildSizeCss()).thenReturn(" @page {size: test;}");
        when(htmlProcessor.replaceResourcesAsBase64Encoded(anyString())).thenAnswer(invocation ->
                invocation.getArgument(0));
        when(htmlProcessor.internalizeLinks(anyString())).thenAnswer(a -> a.getArgument(0));
        String resultHtml = htmlToDocxConverter.preprocessHtml(html);

        assertThat(resultHtml).isEqualTo("""
                <html>
                    <head>
                        test head content before
                        <style>style content @page {size: test;}</style>
                    <base href='http://test' /></head>
                    <body>
                        <span>example text</span>
                    </body>
                </html>""");

        verify(htmlProcessor).replaceResourcesAsBase64Encoded(resultHtml);
    }

    @Test
    void shouldExtendHeadAndAddStyle() {
        String html = """
                <html>
                    <head>
                        test head content before
                    </head>
                    <body>
                        <span>example text</span>
                    </body>
                </html>""";

        when(docxTemplateProcessor.buildBaseUrlHeader()).thenReturn("<base href='http://test' />");
        when(docxTemplateProcessor.buildSizeCss()).thenReturn(" @page {size: test;}");
        when(htmlProcessor.replaceResourcesAsBase64Encoded(anyString())).thenAnswer(invocation ->
                invocation.getArgument(0));
        when(htmlProcessor.internalizeLinks(anyString())).thenAnswer(a -> a.getArgument(0));
        String resultHtml = htmlToDocxConverter.preprocessHtml(html);

        assertThat(resultHtml).isEqualTo("""
                <html>
                    <head>
                        test head content before
                    <base href='http://test' /><style> @page {size: test;}</style></head>
                    <body>
                        <span>example text</span>
                    </body>
                </html>""");

        verify(htmlProcessor).replaceResourcesAsBase64Encoded(resultHtml);
    }

    @Test
    void shouldThrowIllegalArgumentForMalformedHtml() {
        String html = "<span>example text</span>";

        assertThatThrownBy(() -> htmlToDocxConverter.convert(html, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("html is malformed");
    }

    @Test
    void shouldAcceptAttributesAndEmptyTags() {
        String html = """
                <html myAttribute="test">
                    <body/>
                </html>""";

        when(docxTemplateProcessor.buildBaseUrlHeader()).thenReturn("<base href='http://test' />");
        when(docxTemplateProcessor.buildSizeCss()).thenReturn("@page {size: test;}");
        when(htmlProcessor.replaceResourcesAsBase64Encoded(anyString())).thenAnswer(invocation ->
                invocation.getArgument(0));
        when(htmlProcessor.internalizeLinks(anyString())).thenAnswer(a -> a.getArgument(0));
        String resultHtml = htmlToDocxConverter.preprocessHtml(html);

        assertThat(resultHtml).isEqualTo("""
                <html myAttribute="test"><head><base href='http://test' /><style>@page {size: test;}</style></head>
                    <body/>
                </html>""");

        verify(htmlProcessor).replaceResourcesAsBase64Encoded(resultHtml);
    }
}
