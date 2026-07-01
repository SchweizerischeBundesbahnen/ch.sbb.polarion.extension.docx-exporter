package ch.sbb.polarion.extension.docx_exporter.pandoc.service.model;

import ch.sbb.polarion.extension.docx_exporter.rest.model.conversion.ImageDensity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PandocParamsTest {

    @Test
    void testToUrlParams_withBothParams() {
        PandocParams params = PandocParams.builder()
                .orientation("landscape")
                .paperSize("A4")
                .build();

        String result = params.toUrlParams();

        assertTrue(result.startsWith("?"));
        assertTrue(result.contains("orientation=landscape"));
        assertTrue(result.contains("paper_size=A4"));
        assertTrue(result.contains("&"));
    }

    @Test
    void testToUrlParams_withImageDensity() {
        PandocParams params = PandocParams.builder()
                .imageDensity(ImageDensity.DPI_192)
                .build();

        String result = params.toUrlParams();

        assertEquals("?scale_factor=2.0", result);
    }

    @Test
    void testToUrlParams_withAllParams() {
        PandocParams params = PandocParams.builder()
                .orientation("landscape")
                .paperSize("A4")
                .imageDensity(ImageDensity.DPI_300)
                .build();

        String result = params.toUrlParams();

        assertTrue(result.startsWith("?"));
        assertTrue(result.contains("orientation=landscape"));
        assertTrue(result.contains("paper_size=A4"));
        assertTrue(result.contains("scale_factor=3.125"));
    }

    @Test
    void testToUrlParams_withoutImageDensityOmitsScaleFactor() {
        PandocParams params = PandocParams.builder()
                .orientation("portrait")
                .build();

        String result = params.toUrlParams();

        assertFalse(result.contains("scale_factor"));
    }

    @Test
    void testToUrlParams_withOnlyOrientation() {
        PandocParams params = PandocParams.builder()
                .orientation("portrait")
                .build();

        String result = params.toUrlParams();

        assertEquals("?orientation=portrait", result);
    }

    @Test
    void testToUrlParams_withOnlyPaperSize() {
        PandocParams params = PandocParams.builder()
                .paperSize("Letter")
                .build();

        String result = params.toUrlParams();

        assertEquals("?paper_size=Letter", result);
    }

    @Test
    void testToUrlParams_withEmptyStrings() {
        PandocParams params = PandocParams.builder()
                .orientation("")
                .paperSize("")
                .build();

        String result = params.toUrlParams();

        assertEquals("", result);
    }

    @Test
    void testToUrlParams_withNullValues() {
        PandocParams params = PandocParams.builder()
                .build();

        String result = params.toUrlParams();

        assertEquals("", result);
    }

    @Test
    void testToUrlParams_withMixedNullAndValue() {
        PandocParams params = PandocParams.builder()
                .orientation(null)
                .paperSize("A3")
                .build();

        String result = params.toUrlParams();

        assertEquals("?paper_size=A3", result);
    }

    @Test
    void testToUrlParams_withPreserveTableStyles() {
        PandocParams params = PandocParams.builder()
                .orientation("landscape")
                .paperSize("A4")
                .preserveTableStyles(true)
                .build();

        String result = params.toUrlParams();

        assertTrue(result.startsWith("?"));
        assertTrue(result.contains("orientation=landscape"));
        assertTrue(result.contains("paper_size=A4"));
        assertTrue(result.contains("preserve_table_styles=true"));
    }

    @Test
    void testToUrlParams_withOnlyPreserveTableStyles() {
        PandocParams params = PandocParams.builder()
                .preserveTableStyles(true)
                .build();

        String result = params.toUrlParams();

        assertEquals("?preserve_table_styles=true", result);
    }

    @Test
    void testToUrlParams_preserveTableStylesFalseByDefault() {
        PandocParams params = PandocParams.builder()
                .build();

        String result = params.toUrlParams();

        assertFalse(result.contains("preserve_table_styles"));
    }

    @Test
    void testFromJson_validJson() {
        String json = "{\"orientation\":\"landscape\",\"paperSize\":\"A4\"}";

        PandocParams params = PandocParams.fromJson(json);

        assertNotNull(params);
        assertEquals("landscape", params.getOrientation());
        assertEquals("A4", params.getPaperSize());
    }

    @Test
    void testFromJson_withPreserveTableStyles() {
        String json = "{\"orientation\":\"landscape\",\"paperSize\":\"A4\",\"preserveTableStyles\":true}";

        PandocParams params = PandocParams.fromJson(json);

        assertNotNull(params);
        assertEquals("landscape", params.getOrientation());
        assertEquals("A4", params.getPaperSize());
        assertTrue(params.isPreserveTableStyles());
    }

    @Test
    void testFromJson_partialJson() {
        String json = "{\"orientation\":\"portrait\"}";

        PandocParams params = PandocParams.fromJson(json);

        assertNotNull(params);
        assertEquals("portrait", params.getOrientation());
        assertNull(params.getPaperSize());
    }

    @Test
    void testFromJson_emptyJson() {
        String json = "{}";

        PandocParams params = PandocParams.fromJson(json);

        assertNotNull(params);
        assertNull(params.getOrientation());
        assertNull(params.getPaperSize());
    }

    @Test
    void testFromJson_invalidJson() {
        String json = "invalid json";

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> PandocParams.fromJson(json));

        assertTrue(exception.getMessage().contains("Failed to parse PandocParams from JSON"));
    }

    @Test
    void testFromJson_nullJson() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> PandocParams.fromJson(null));

        assertTrue(exception.getMessage().contains("Failed to parse PandocParams from JSON"));
    }

    @Test
    void testBuilder() {
        PandocParams params = PandocParams.builder()
                .orientation("landscape")
                .paperSize("A4")
                .build();

        assertNotNull(params);
        assertEquals("landscape", params.getOrientation());
        assertEquals("A4", params.getPaperSize());
    }

    @Test
    void testGettersAndSetters() {
        PandocParams params = PandocParams.builder().build();
        params.setOrientation("portrait");
        params.setPaperSize("Letter");

        assertEquals("portrait", params.getOrientation());
        assertEquals("Letter", params.getPaperSize());
    }

    @Test
    void testEqualsAndHashCode() {
        PandocParams params1 = PandocParams.builder()
                .orientation("landscape")
                .paperSize("A4")
                .build();

        PandocParams params2 = PandocParams.builder()
                .orientation("landscape")
                .paperSize("A4")
                .build();

        assertEquals(params1, params2);
        assertEquals(params1.hashCode(), params2.hashCode());
    }

    @Test
    void testToString() {
        PandocParams params = PandocParams.builder()
                .orientation("landscape")
                .paperSize("A4")
                .build();

        String toString = params.toString();

        assertNotNull(toString);
        assertTrue(toString.contains("landscape"));
        assertTrue(toString.contains("A4"));
    }
}
