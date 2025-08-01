package ch.sbb.polarion.extension.docx_exporter;

import ch.sbb.polarion.extension.generic.test_extensions.PlatformContextMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(PlatformContextMockExtension.class)
class ExtensionBundleActivatorTest {

    @Test
    void testBundleActivator() {
        assertEquals("docx-exporter", new ExtensionBundleActivator().getExtensions().keySet().iterator().next());
    }

}
