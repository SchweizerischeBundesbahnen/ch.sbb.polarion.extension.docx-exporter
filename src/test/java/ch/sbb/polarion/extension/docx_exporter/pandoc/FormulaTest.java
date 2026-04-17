package ch.sbb.polarion.extension.docx_exporter.pandoc;

import ch.sbb.polarion.extension.docx_exporter.pandoc.service.model.PandocParams;
import ch.sbb.polarion.extension.docx_exporter.util.HtmlProcessor;
import ch.sbb.polarion.extension.docx_exporter.util.JSoupUtils;
import org.docx4j.XmlUtils;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SkipTestWhenParamNotSet
class FormulaTest extends BasePandocTest {

    @Test
    void polarionFormulaImagesAreConvertedToNativeWordMath() throws Exception {
        String polarionHtml = readHtmlResource("testFormula");

        // Apply only the Polarion-formula → Pandoc-math-script conversion step. We intentionally bypass the rest of
        // HtmlProcessor.processHtmlForExport here to keep this test focused on the formula conversion + Pandoc behaviour.
        Document document = JSoupUtils.parseHtml(polarionHtml);
        new HtmlProcessor(null, null, null).convertPolarionFormulas(document);
        String preparedHtml = document.outerHtml();

        byte[] docBytes = exportToDOCX(preparedHtml, readTemplate("reference_template"), PandocParams.builder().build());
        assertNotNull(docBytes);
        writeReportDocx("polarionFormulaImagesAreConvertedToNativeWordMath_generated", docBytes);

        WordprocessingMLPackage pkg = WordprocessingMLPackage.load(new ByteArrayInputStream(docBytes));
        MainDocumentPart documentPart = pkg.getMainDocumentPart();

        List<Object> oMathNodes = documentPart.getJAXBNodesViaXPath("//m:oMath", true);
        assertFalse(oMathNodes.isEmpty(), "Expected at least one <m:oMath> in the generated DOCX");
        assertTrue(oMathNodes.size() >= 2, "Expected both inline and display formulas to produce <m:oMath> elements, got " + oMathNodes.size());

        // Verify the LaTeX actually reached the DOCX as math, not as plain text / dropped content.
        String documentXml = XmlUtils.marshaltoString(documentPart.getJaxbElement(), true, true);
        assertTrue(documentXml.contains("<m:oMath"), "document.xml should contain <m:oMath> elements");
        assertFalse(documentXml.contains("polarion-rte-formula"), "No residual Polarion formula markers should remain in the DOCX");
    }
}
