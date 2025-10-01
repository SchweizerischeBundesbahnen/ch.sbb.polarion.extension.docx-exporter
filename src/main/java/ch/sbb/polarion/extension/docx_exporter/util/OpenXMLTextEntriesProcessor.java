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
import org.docx4j.wml.SdtElement;
import org.docx4j.wml.Text;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Processor for text entries in OpenXML (docx) documents. It traverses the document's headers/footers
 * and applies a provided text processing function to all text entries. This is useful for tasks such as
 * template variable replacement or text transformations.
 */
@SuppressWarnings("unchecked")
public class OpenXMLTextEntriesProcessor implements BundleJarsPrioritizingRunnable {

    public static final String PARAM_PROCESS_TEMPLATE = "template";
    public static final String PARAM_PROCESS_FUNCTION = "processFunction";
    public static final String PARAM_PROCESS_RESULT = "result";

    private final Logger logger = Logger.getLogger(OpenXMLTextEntriesProcessor.class);

    @Override
    public Map<String, Object> run(Map<String, Object> params) {
        return Map.of(PARAM_PROCESS_RESULT, processTextEntries(
                (byte[]) params.get(PARAM_PROCESS_TEMPLATE),
                (UnaryOperator<String>) params.get(PARAM_PROCESS_FUNCTION)
        ));
    }

    public byte[] processTextEntries(byte[] document, UnaryOperator<String> processTextFunction) {
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
            logger.error("Failed to process docx template", e);
            return document;
        }
    }

    private void walkContentRecursively(List<Object> content, Context context) {
        if (content != null) {
            for (Object item : content) {
                processContentItem(item, context);
            }
        }
    }

    private void processContentItem(Object item, Context context) {
        if (item instanceof Text text) {
            processTextValue(text, context);
        } else if (item instanceof JAXBElement<?> jaxbElement) {
            processContentItem(jaxbElement.getValue(), context);
        } else if (item instanceof P paragraph) {
            cleanProofErrors(paragraph);
            VariablePrepare.joinupRuns(paragraph);
            walkContentRecursively(paragraph.getContent(), context);
        } else if (item instanceof ContentAccessor accessor) {
            walkContentRecursively(accessor.getContent(), context);
        } else if (item instanceof SdtElement sdtElement && sdtElement.getSdtContent() != null) {
            walkContentRecursively(sdtElement.getSdtContent().getContent(), context);
        }
    }

    private void processTextValue(Text text, Context context) {
        String textValue = text.getValue();
        if (textValue != null && (textValue.contains("$") || textValue.contains("{"))) {
            String processed = context.processTextFunction.apply(textValue);
            if (!Objects.equals(textValue, processed)) {
                text.setValue(processed);
            }
        }
    }

    private void cleanProofErrors(P paragraph) {
        paragraph.getContent().removeIf(ProofErr.class::isInstance);
    }

    private static class Context {
        UnaryOperator<String> processTextFunction;

        public Context(UnaryOperator<String> processTextFunction) {
            this.processTextFunction = processTextFunction;
        }
    }

}
