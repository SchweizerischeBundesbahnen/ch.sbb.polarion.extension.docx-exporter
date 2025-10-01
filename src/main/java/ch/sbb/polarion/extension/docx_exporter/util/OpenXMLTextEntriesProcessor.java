package ch.sbb.polarion.extension.docx_exporter.util;

import ch.sbb.polarion.extension.generic.util.BundleJarsPrioritizingRunnable;
import com.polarion.core.util.logging.Logger;
import jakarta.xml.bind.JAXBElement;
import org.docx4j.model.datastorage.migration.VariablePrepare;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.relationships.Namespaces;
import org.docx4j.relationships.Relationship;
import org.docx4j.wml.ContentAccessor;
import org.docx4j.wml.P;
import org.docx4j.wml.ProofErr;
import org.docx4j.wml.R;
import org.docx4j.wml.SdtBlock;
import org.docx4j.wml.SdtRun;
import org.docx4j.wml.Tbl;
import org.docx4j.wml.Tc;
import org.docx4j.wml.Text;
import org.docx4j.wml.Tr;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Processor for text entries in OpenXML (docx) documents. It traverses the document's headers/footers
 * and applies a provided text processing function to all text entries. This is useful for tasks such as
 * template variable replacement or text transformations.
 */
@SuppressWarnings("unchecked")
public class OpenXMLTextEntriesProcessor implements BundleJarsPrioritizingRunnable {

    private final Logger logger = Logger.getLogger(OpenXMLTextEntriesProcessor.class);

    @Override
    public Map<String, Object> run(Map<String, Object> params) {
        return Map.of(DocxTemplateProcessor.PARAM_PROCESS_RESULT, processTextEntries(
                (byte[]) params.get(DocxTemplateProcessor.PARAM_PROCESS_TEMPLATE),
                (Function<String, String>) params.get(DocxTemplateProcessor.PARAM_PROCESS_FUNCTION)
        ));
    }

    public byte[] processTextEntries(byte[] document, Function<String, String> processTextFunction) {
        try {
            WordprocessingMLPackage wordPackage = WordprocessingMLPackage.load(new ByteArrayInputStream(document));
            List<Relationship> relationships = wordPackage.getMainDocumentPart().getRelationshipsPart().getRelationships().getRelationship();
            for (Relationship relationship : relationships) {
                String type = relationship.getType();
                if (Namespaces.HEADER.equals(type) || Namespaces.FOOTER.equals(type)) {
                    ContentAccessor headerPart = (ContentAccessor) wordPackage.getMainDocumentPart().getRelationshipsPart().getPart(relationship);
                    walkContentRecursively(headerPart.getContent(), new Context(processTextFunction));
                }
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            wordPackage.save(outputStream);
            return outputStream.toByteArray();

        } catch (Exception e) {
            logger.info("Failed to process docx template: " + e.getMessage());
            return document;
        }
    }

    private void walkContentRecursively(List<Object> content, Context context) {
        if (content == null) return;

        for (Object item : content) {
            if (item instanceof P paragraph) {
                cleanProofErrors(paragraph);
                VariablePrepare.joinupRuns(paragraph);
                processTextContent(paragraph.getContent(), context);
            } else if (item instanceof Tbl table) {
                for (Object tableChild : table.getContent()) {
                    if (tableChild instanceof Tr row) {
                        for (Object rowChild : row.getContent()) {
                            if (rowChild instanceof Tc cell) {
                                walkContentRecursively(cell.getContent(), context);
                            }
                        }
                    }
                }
            } else if (item instanceof SdtBlock sdtBlock) {
                if (sdtBlock.getSdtContent() != null) {
                    walkContentRecursively(sdtBlock.getSdtContent().getContent(), context);
                }
            }
        }
    }

    private void processTextContent(List<Object> content, Context context) {
        for (Object item : content) {
            if (item instanceof R run) {
                for (Object runChild : run.getContent()) {
                    if (runChild instanceof Text text) {
                        processTextValue(text, context);
                    } else if (runChild instanceof JAXBElement<?> jaxbElement && jaxbElement.getValue() instanceof Text text) {
                        processTextValue(text, context);
                    }
                }
            } else if (item instanceof SdtRun sdtRun) {
                if (sdtRun.getSdtContent() != null) {
                    processTextContent(sdtRun.getSdtContent().getContent(), context);
                }
            }
        }
    }

    private void processTextValue(Text text, Context context) {
        String textValue = text.getValue();
        if (textValue != null && (textValue.contains("$") || textValue.contains("{"))) {
            String processed = context.processTextFunction.apply(textValue);
            if (!Objects.equals(textValue, processed)) {
                text.setValue(processed);
                context.atLeastOneReplaced = true;
            }
        }
    }

    private void cleanProofErrors(P paragraph) {
        paragraph.getContent().removeIf(item -> item instanceof ProofErr);
    }

    private static class Context {
        Function<String, String> processTextFunction;
        boolean atLeastOneReplaced = false;

        public Context(Function<String, String> processTextFunction) {
            this.processTextFunction = processTextFunction;
        }
    }

}
