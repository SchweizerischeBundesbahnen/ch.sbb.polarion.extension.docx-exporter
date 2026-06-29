package ch.sbb.polarion.extension.docx_exporter.rest.model.conversion;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageDensityTest {

    @Test
    void shouldResolveValueIgnoringCase() {
        assertThat(ImageDensity.fromString("dpi_300")).isEqualTo(ImageDensity.DPI_300);
        assertThat(ImageDensity.fromString("DPI_96")).isEqualTo(ImageDensity.DPI_96);
    }

    @Test
    void shouldReturnNullWhenNameIsNull() {
        assertThat(ImageDensity.fromString(null)).isNull();
    }

    @Test
    void shouldThrowBadRequestWhenNameIsUnsupported() {
        assertThatThrownBy(() -> ImageDensity.fromString("DPI_1234"))
                .isInstanceOf(WebApplicationException.class)
                .satisfies(e -> {
                    Response response = ((WebApplicationException) e).getResponse();
                    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
                    assertThat(response.getEntity()).isEqualTo("Unsupported value for imageDensity parameter: DPI_1234");
                });
    }

    @Test
    void shouldExposeScale() {
        assertThat(ImageDensity.DPI_300.getScale()).isEqualTo(3.125);
    }
}
