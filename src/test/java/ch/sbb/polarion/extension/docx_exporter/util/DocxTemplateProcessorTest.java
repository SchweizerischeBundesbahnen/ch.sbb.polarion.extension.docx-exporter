package ch.sbb.polarion.extension.docx_exporter.util;

import ch.sbb.polarion.extension.docx_exporter.TestStringUtils;
import ch.sbb.polarion.extension.docx_exporter.rest.model.conversion.ExportParams;
import ch.sbb.polarion.extension.docx_exporter.rest.model.documents.DocumentData;
import ch.sbb.polarion.extension.docx_exporter.util.placeholder.PlaceholderProcessor;
import ch.sbb.polarion.extension.docx_exporter.util.velocity.VelocityEvaluator;
import ch.sbb.polarion.extension.generic.test_extensions.BundleJarsPrioritizingRunnableMockExtension;
import ch.sbb.polarion.extension.generic.test_extensions.PlatformContextMockExtension;
import ch.sbb.polarion.extension.generic.test_extensions.TransactionalExecutorExtension;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.stream.Stream;

import static ch.sbb.polarion.extension.docx_exporter.pandoc.BasePandocTest.readTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, PlatformContextMockExtension.class, TransactionalExecutorExtension.class, BundleJarsPrioritizingRunnableMockExtension.class})
class DocxTemplateProcessorTest {

    @Mock
    private VelocityEvaluator velocityEvaluator;

    @Mock
    private PlaceholderProcessor placeholderProcessor;

    private static Stream<Arguments> paramsForProcessHtmlTemplate() {
        return Stream.of(
                Arguments.of("""
                        <?xml version='1.0' encoding='UTF-8'?>
                        <!DOCTYPE html PUBLIC '-//W3C//DTD XHTML 1.0 Strict//EN' 'http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd'>
                        <html lang='en' xml:lang='en' xmlns='http://www.w3.org/1999/xhtml'>
                        <head>
                            <title>testDocumentName</title>
                            <meta content='text/html; charset=UTF-8' http-equiv='Content-Type'/>
                            <link crossorigin='anonymous' href='/polarion/ria/font-awesome-4.0.3/css/font-awesome.css' referrerpolicy='no-referrer' rel='stylesheet'/>
                        </head>
                        <body>
                        test html content
                        </body>
                        </html>""".indent(0).trim()),

                Arguments.of("""
                        <?xml version='1.0' encoding='UTF-8'?>
                        <!DOCTYPE html PUBLIC '-//W3C//DTD XHTML 1.0 Strict//EN' 'http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd'>
                        <html lang='en' xml:lang='en' xmlns='http://www.w3.org/1999/xhtml'>
                        <head>
                            <title>testDocumentName</title>
                            <meta content='text/html; charset=UTF-8' http-equiv='Content-Type'/>
                            <link crossorigin='anonymous' href='/polarion/ria/font-awesome-4.0.3/css/font-awesome.css' referrerpolicy='no-referrer' rel='stylesheet'/>
                        </head>
                        <body>
                        test html content
                        </body>
                        </html>""".indent(0).trim())
        );
    }

    @ParameterizedTest
    @MethodSource("paramsForProcessHtmlTemplate")
    void shouldProcessHtmlTemplate(String expectedResult) {

        // Act
        String resultHtml = new DocxTemplateProcessor(velocityEvaluator, placeholderProcessor).processUsing("testDocumentName", "test html content");

        // Assert
        assertThat(TestStringUtils.removeNonsensicalSymbols(resultHtml).replaceAll(" ", ""))
                .isEqualTo(TestStringUtils.removeNonsensicalSymbols(expectedResult).replaceAll(" ", ""));

    }

    @Test
    void testBuildSizeCss() {
        // Act
        String css = new DocxTemplateProcessor(velocityEvaluator, placeholderProcessor).buildSizeCss();

        // Assert
        assertThat(css).isEqualTo(" @page {size: A4 %portrait;}");
    }

    @Test
    @SneakyThrows
    void testProcessDocxTemplate() {
        // Arrange
        byte[] templateContent = readTemplate("reference_template_with_velocity_and_placeholder");
        DocumentData<?> documentData = mock(DocumentData.class);
        ExportParams exportParams = ExportParams.builder().build();

        when(velocityEvaluator.evaluateVelocityExpressions(any(), anyString())).thenAnswer(invocation -> invocation.getArgument(1));
        when(placeholderProcessor.replacePlaceholders(any(), any(), anyString())).thenAnswer(invocation -> invocation.getArgument(2));

        // Act & assert
        assertDoesNotThrow(() -> new DocxTemplateProcessor(velocityEvaluator, placeholderProcessor).processDocxTemplate(templateContent, documentData, exportParams));

        // Assert
        verify(velocityEvaluator, times(1)).evaluateVelocityExpressions(any(), eq("$document.getId()"));
        verify(velocityEvaluator, times(1)).evaluateVelocityExpressions(any(), eq("$document.title"));
        verify(velocityEvaluator, times(1)).evaluateVelocityExpressions(any(), eq("{{ TIMESTAMP }}"));
        verify(placeholderProcessor, times(1)).replacePlaceholders(any(), any(), eq("$document.getId()"));
        verify(placeholderProcessor, times(1)).replacePlaceholders(any(), any(), eq("$document.title"));
        verify(placeholderProcessor, times(1)).replacePlaceholders(any(), any(), eq("{{ TIMESTAMP }}"));

        // Verify that exceptions in processors are handled properly
        when(placeholderProcessor.replacePlaceholders(any(), any(), eq("$document.getId()"))).thenThrow(new RuntimeException("Placeholder processing failed"));
        assertDoesNotThrow(() -> new DocxTemplateProcessor(velocityEvaluator, placeholderProcessor).processDocxTemplate(templateContent, documentData, exportParams));
    }
}
