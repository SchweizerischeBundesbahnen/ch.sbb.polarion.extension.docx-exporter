package ch.sbb.polarion.extension.docx_exporter.rest.model.documents;

import com.polarion.alm.shared.api.model.document.Document;
import com.polarion.alm.tracker.model.IModule;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class UniqueObjectConverterTest {

    @Test
    void testDocument() {
        Document document = mock(Document.class);
        when(document.getOldApi()).thenReturn(mock(IModule.class));
        new UniqueObjectConverter(document);
        verify(document, times(1)).getOldApi();
    }
}
