package ch.sbb.polarion.extension.docx_exporter.rest.controller;

import ch.sbb.polarion.extension.generic.settings.SettingId;
import ch.sbb.polarion.extension.generic.settings.SettingName;
import ch.sbb.polarion.extension.docx_exporter.rest.model.settings.localization.LocalizationModel;
import ch.sbb.polarion.extension.docx_exporter.rest.model.settings.stylepackage.DocIdentifier;
import ch.sbb.polarion.extension.docx_exporter.rest.model.settings.stylepackage.StylePackageWeightInfo;
import ch.sbb.polarion.extension.docx_exporter.service.DocxExporterPolarionService;
import ch.sbb.polarion.extension.docx_exporter.settings.LocalizationSettings;
import ch.sbb.polarion.extension.docx_exporter.util.LocalizationHelper;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import net.sf.okapi.lib.xliff2.reader.XLIFFReader;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataParam;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Hidden
@Path("/internal")
@Tag(name = "Settings", description = "Operations related to Docx-exporter settings management")
public class SettingsInternalController {

    private final DocxExporterPolarionService docxExporterPolarionService = new DocxExporterPolarionService();

    @GET
    @Path("/settings/localization/names/{name}/download")
    @Produces(MediaType.APPLICATION_XML)
    @Operation(summary = "Downloads localization values by name of localization settings",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Localization file downloaded successfully")
            }
    )
    public Response downloadTranslations(
            @PathParam("name") String name,
            @QueryParam("language") String language,
            @QueryParam("revision") String revision,
            @QueryParam("scope") String scope
    ) {
        final LocalizationSettings localizationSettings = new LocalizationSettings();
        final LocalizationModel localizationModel = localizationSettings.read(scope, SettingId.fromName(name), revision);
        final Map<String, String> localizationMap = localizationModel.getLocalizationMap(language);
        final String xliff = LocalizationHelper.xmlFromMap(language, localizationMap);

        return Response.ok(xliff.getBytes(StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + language + ".xlf")
                .build();
    }

    @POST
    @Path("/settings/localization/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Uploads localization values",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Localization file uploaded successfully")
            }
    )
    public Map<String, String> uploadTranslations(
            @FormDataParam("file") FormDataBodyPart file,
            @QueryParam("language") String language,
            @QueryParam("scope") String scope
    ) {
        file.setMediaType(MediaType.TEXT_PLAIN_TYPE);
        String xliff = file.getValueAs(String.class);
        XLIFFReader.validate(xliff, null);
        return LocalizationHelper.getTranslationsMapForLanguage(xliff);
    }

    @POST
    @Path("/settings/style-package/suitable-names")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Tag(name = "Settings")
    @Operation(summary = "Get list of style packages suitable for the specified list of documents (sorted by weight)",
            requestBody = @RequestBody(description = "List of document identifiers",
                    required = true,
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = DocIdentifier.class)),
                            mediaType = MediaType.APPLICATION_JSON
                    )
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successfully retrieved the list of style package names")
            }
    )
    public Collection<SettingName> getSuitableStylePackageNames(List<DocIdentifier> docIdentifiers) {
        if (docIdentifiers.isEmpty()) {
            throw new BadRequestException("At least one document identifier required");
        }
        return docxExporterPolarionService.getSuitableStylePackages(docIdentifiers);
    }

    @GET
    @Path("/settings/style-package/weights")
    @Produces(MediaType.APPLICATION_JSON)
    @Tag(name = "Settings")
    @Operation(summary = "Get full list of available style packages for the specific scope with the weight information",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successfully retrieved the list of style package names with the weight information")
            }
    )
    public Collection<StylePackageWeightInfo> getStylePackageWeights(@QueryParam("scope") String scope) {
        return docxExporterPolarionService.getStylePackagesWeights(scope);
    }

    @POST
    @Path("/settings/style-package/weights")
    @Consumes(MediaType.APPLICATION_JSON)
    @Tag(name = "Settings")
    @Operation(summary = "Update weight information for the provided style packages",
            requestBody = @RequestBody(description = "Style packages list with the weight information",
                    required = true,
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = StylePackageWeightInfo.class)),
                            mediaType = MediaType.APPLICATION_JSON
                    )
            )
    )
    public void updateStylePackageWeights(List<StylePackageWeightInfo> stylePackageWeights) {
        docxExporterPolarionService.updateStylePackagesWeights(stylePackageWeights);
    }

}
