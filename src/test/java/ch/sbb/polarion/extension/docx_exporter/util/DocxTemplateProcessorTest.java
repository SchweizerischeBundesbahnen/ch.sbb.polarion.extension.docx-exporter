package ch.sbb.polarion.extension.docx_exporter.util;

import ch.sbb.polarion.extension.docx_exporter.TestStringUtils;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class DocxTemplateProcessorTest {
    private final DocxTemplateProcessor docxTemplateProcessor = new DocxTemplateProcessor();

    @ParameterizedTest
    @MethodSource("paramsForProcessHtmlTemplate")
    void shouldProcessHtmlTemplate(String expectedResult) {

        // Act
        String resultHtml = docxTemplateProcessor.processUsing("testDocumentName", "test html content");

        // Assert
        assertThat(TestStringUtils.removeNonsensicalSymbols(resultHtml).replaceAll(" ", ""))
                .isEqualTo(TestStringUtils.removeNonsensicalSymbols(expectedResult).replaceAll(" ", ""));

    }

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
}
