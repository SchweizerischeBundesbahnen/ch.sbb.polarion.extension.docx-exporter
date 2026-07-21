package ch.sbb.polarion.extension.docx_exporter.pandoc;

import ch.sbb.polarion.extension.docx_exporter.pandoc.service.model.PandocParams;
import ch.sbb.polarion.extension.docx_exporter.pandoc.DocxStructureInspector.Paragraph;
import org.junit.jupiter.api.Test;

import java.util.List;

import static ch.sbb.polarion.extension.docx_exporter.pandoc.DocxStructureInspector.fmt;
import static ch.sbb.polarion.extension.docx_exporter.pandoc.DocxStructureInspector.paragraphs;
import static ch.sbb.polarion.extension.docx_exporter.pandoc.DocxStructureInspector.plain;
import static ch.sbb.polarion.extension.docx_exporter.pandoc.DocxStructureInspector.seg;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration test of "general formatting" case.
 * <p>
 * The source Polarion LiveDoc renders a heading plus two paragraphs exercising the full range of inline
 * rich-text formatting. This asserts the exact ordered sequence of formatted text segments pandoc-service
 * produces in {@code word/document.xml}, each with its expected text and its expected run properties
 * (bold, italic, underline, strike-through, super/subscript, text color, highlight fill, font size).
 */
@SkipTestWhenParamNotSet
class FormattingConversionTest extends BasePandocTest {

    @Test
    void formattingIsConvertedToDocx() throws Exception {
        byte[] docBytes = exportToDOCX(readHtmlResource("formattingTest"), readTemplate("reference_template"),
                PandocParams.builder().build());
        assertNotNull(docBytes);
        writeReportDocx("formattingTest_generated", docBytes);

        List<Paragraph> expected = List.of(
                new Paragraph("Title", List.of(plain("Formatting Test")), List.of()),
                new Paragraph("Heading1", List.of(plain("Heading")), List.of()),
                new Paragraph("BodyText", List.of(
                        plain("There are many variations of passages of "),
                        seg(fmt().bold(), "Lorem Ipsum"),
                        plain(" available, but "),
                        seg(fmt().italic(), "the majority"),
                        plain(" have suffered alteration in some form, by "),
                        seg(fmt().underline(), "injected humour"),
                        plain(", or "),
                        seg(fmt().strike(), "randomised words"),
                        plain(" which don't look even "),
                        seg(fmt().highlight("FFFF33"), "slightly believable"),
                        plain(". "),
                        seg(fmt().color("FF0000"), "If you are going"),
                        seg(fmt().superscript(), "1"),
                        plain(" to use a passage of "),
                        seg(fmt().bold().italic().underline().color("FF0000"), "Lorem Ipsum"),
                        plain(", you need to be sure there isn't anything embarrassing hidden in the middle of text. "
                                + "All the Lorem Ipsum generators on the Internet tend to repeat predefined chunks as "
                                + "necessary, making this the first true generator on the Internet. It uses a dictionary "
                                + "of over "),
                        seg(fmt().subscript(), "200"),
                        plain(" Latin words, "),
                        seg(fmt().color("FF0000"), "combined "),
                        seg(fmt().color("FF0000").highlight("99FFFF"), "with"),
                        plain(" a handful of model sentence structures, to generate Lorem Ipsum which looks reasonable. "),
                        seg(fmt().italic(), "The generated "),
                        seg(fmt().bold().italic(), "Lorem "),
                        seg(fmt().bold().italic().underline(), "Ipsum"),
                        seg(fmt().italic().underline(), " is therefore"),
                        seg(fmt().italic(), " always free from repetition, "),
                        seg(fmt().italic().highlight("99FFFF"), "injected "),
                        seg(fmt().bold().italic().color("FF0000").highlight("99FFFF"), "humour"),
                        seg(fmt().italic(), ", or non-characteristic words etc"),
                        plain(".")), List.of()),
                new Paragraph("BodyText", List.of(
                        seg(fmt().bold(), "Quo cognito Consta"),
                        seg(fmt().bold().underline(), "ntius ultr"),
                        seg(fmt().underline(), "a"),
                        seg(fmt().underline().size(24), " mortalem"),
                        seg(fmt().underline().strike().size(24), " mo"),
                        seg(fmt().underline().strike(), "du"),
                        seg(fmt().strike(), "m"),
                        seg(fmt().strike().color("6633FF"), " exar"),
                        seg(fmt().italic().strike().color("6633FF"), "sit "),
                        seg(fmt().italic().strike(), "a"),
                        seg(fmt().italic(), "c nequo casu"),
                        seg(fmt().italic().strike().underline(), " idem"),
                        seg(fmt().strike().underline(), " Gallus d"),
                        seg(fmt().strike().underline().highlight("CC33CC"), "e fut"),
                        seg(fmt().highlight("CC33CC"), "uris incertus agi"),
                        plain("t"),
                        seg(fmt().strike().underline(), "are quaedam c"),
                        plain("onducentia "),
                        seg(fmt().italic().underline().superscript(), "saluti "),
                        plain("suae per "),
                        seg(fmt().bold(), "itinera conar"),
                        seg(fmt().bold().size(32), "etur, re"),
                        seg(fmt().bold().italic().size(32), "moti sunt "),
                        seg(fmt().bold().italic().underline().size(32), "om"),
                        seg(fmt().bold().underline(), "nes d"),
                        seg(fmt().bold().strike().underline(), "e industria mi"),
                        seg(fmt().bold().strike(), "lites agente"),
                        seg(fmt().bold(), "s in civitatibus perviis.")), List.of()));

        assertEquals(expected, paragraphs(docBytes));
    }
}
