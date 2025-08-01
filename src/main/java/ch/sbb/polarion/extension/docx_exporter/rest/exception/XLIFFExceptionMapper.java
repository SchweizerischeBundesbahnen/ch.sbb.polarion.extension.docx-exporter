package ch.sbb.polarion.extension.docx_exporter.rest.exception;

import ch.sbb.polarion.extension.generic.rest.model.ErrorEntity;
import com.polarion.core.util.logging.Logger;
import net.sf.okapi.lib.xliff2.XLIFFException;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
public class XLIFFExceptionMapper implements ExceptionMapper<XLIFFException> {
    private final Logger logger = Logger.getLogger(XLIFFExceptionMapper.class);

    public Response toResponse(XLIFFException e) {
        logger.error("XLIFF format error: " + e.getMessage(), e);
        return Response.status(Response.Status.BAD_REQUEST.getStatusCode())
                .entity(new ErrorEntity(e.getMessage()))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
