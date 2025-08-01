package ch.sbb.polarion.extension.docx_exporter.rest.controller;

import ch.sbb.polarion.extension.generic.configuration.ConfigurationStatus;
import ch.sbb.polarion.extension.generic.configuration.ConfigurationStatusProvider;
import ch.sbb.polarion.extension.docx_exporter.util.configuration.CORSStatusProvider;
import ch.sbb.polarion.extension.docx_exporter.util.configuration.DefaultSettingsStatusProvider;
import ch.sbb.polarion.extension.docx_exporter.util.configuration.DleToolbarStatusProvider;
import ch.sbb.polarion.extension.docx_exporter.util.configuration.DocumentPropertiesPaneStatusProvider;
import ch.sbb.polarion.extension.docx_exporter.util.configuration.PandocStatusProvider;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.jetbrains.annotations.NotNull;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Hidden
@Path("/internal")
public class ConfigurationInternalController {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/configuration/default-settings")
    @Operation(
            summary = "Checks default settings configuration",
            responses = {
                    @ApiResponse(responseCode = "200",
                            description = "Successfully retrieved default settings configuration",
                            content = @Content(schema = @Schema(implementation = ConfigurationStatus.class))
                    )
            }
    )
    public @NotNull ConfigurationStatus checkDefaultSettings(@QueryParam("scope") @DefaultValue("") String scope) {
        return new DefaultSettingsStatusProvider().getStatus(ConfigurationStatusProvider.Context.builder().scope(scope).build());
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/configuration/document-properties-pane-config")
    @Operation(
            summary = "Checks document properties pane configuration",
            responses = {
                    @ApiResponse(responseCode = "200",
                            description = "Successfully retrieved document properties pane configuration",
                            content = @Content(schema = @Schema(implementation = ConfigurationStatus.class))
                    )
            }
    )
    public @NotNull ConfigurationStatus checkDocumentPropertiesPaneConfig(@QueryParam("scope") @DefaultValue("") String scope) {
        return new DocumentPropertiesPaneStatusProvider().getStatus(ConfigurationStatusProvider.Context.builder().scope(scope).build());
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/configuration/dle-toolbar-config")
    @Operation(
            summary = "Checks DLE Toolbar configuration",
            responses = {
                    @ApiResponse(responseCode = "200",
                            description = "Successfully retrieved DLE Toolbar configuration",
                            content = @Content(schema = @Schema(implementation = ConfigurationStatus.class))
                    )
            }
    )
    public @NotNull ConfigurationStatus checkDleToolbarConfig() {
        return new DleToolbarStatusProvider().getStatus(ConfigurationStatusProvider.Context.builder().build());
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/configuration/cors-config")
    @Operation(
            summary = "Checks CORS configuration",
            description = "Retrieves the status of the CORS configuration.",
            responses = {
                    @ApiResponse(responseCode = "200",
                            description = "Successfully retrieved CORS configuration",
                            content = @Content(schema = @Schema(implementation = ConfigurationStatus.class))
                    )
            }
    )
    public @NotNull ConfigurationStatus checkCORSConfig() {
        return new CORSStatusProvider().getStatus(ConfigurationStatusProvider.Context.builder().build());
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/configuration/pandoc")
    @Operation(
            summary = "Checks Pandoc configuration",
            description = "Retrieves the status of the Pandoc configuration.",
            responses = {
                    @ApiResponse(responseCode = "200",
                            description = "Successfully retrieved Pandoc configuration",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ConfigurationStatus.class)))
                    )
            }
    )
    public @NotNull List<ConfigurationStatus> checkPandoc() {
        return new PandocStatusProvider().getStatuses(ConfigurationStatusProvider.Context.builder().build());
    }
}
