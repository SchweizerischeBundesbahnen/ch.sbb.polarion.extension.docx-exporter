package ch.sbb.polarion.extension.docx_exporter.test_extensions;

import ch.sbb.polarion.extension.docx_exporter.rest.model.conversion.ExportParams;
import ch.sbb.polarion.extension.docx_exporter.rest.model.documents.DocumentData;
import ch.sbb.polarion.extension.docx_exporter.util.DocumentDataFactory;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.*;

public class DocumentDataFactoryMockExtension implements BeforeEachCallback, AfterEachCallback {

    private MockedStatic<DocumentDataFactory> documentDataFactoryMockedStatic;
    // The mocks of the test of this thread: test classes run in parallel, a class's tests in one thread
    private static final ThreadLocal<Map<ExportParams, DocumentData<?>>> DOCUMENT_DATA_MOCKS = ThreadLocal.withInitial(HashMap::new);

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        documentDataFactoryMockedStatic = mockStatic(DocumentDataFactory.class);

        documentDataFactoryMockedStatic.when(() -> DocumentDataFactory.getDocumentData(any(ExportParams.class), anyBoolean())).thenAnswer(invocation -> {
            ExportParams params = invocation.getArgument(0);
            DocumentData<?> value = DOCUMENT_DATA_MOCKS.get().get(params);
            if (value != null) {
                return value;
            } else {
                throw new IllegalArgumentException("No mock registered for given ExportParams: " + params);
            }
        });
    }

    public void register(ExportParams exportParams, DocumentData<?> mock) {
        if (DOCUMENT_DATA_MOCKS.get().containsKey(exportParams)) {
            throw new IllegalStateException("Mock already registered for given ExportParams: " + exportParams);
        }
        DOCUMENT_DATA_MOCKS.get().put(exportParams, mock);
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        if (documentDataFactoryMockedStatic != null) {
            documentDataFactoryMockedStatic.close();
        }
        DOCUMENT_DATA_MOCKS.remove();
    }

}
