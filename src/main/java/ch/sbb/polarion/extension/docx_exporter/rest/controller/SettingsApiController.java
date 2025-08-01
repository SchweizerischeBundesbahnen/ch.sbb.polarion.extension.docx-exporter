package ch.sbb.polarion.extension.docx_exporter.rest.controller;

import ch.sbb.polarion.extension.generic.rest.filter.Secured;
import ch.sbb.polarion.extension.generic.service.PolarionService;
import ch.sbb.polarion.extension.generic.settings.SettingName;
import ch.sbb.polarion.extension.docx_exporter.rest.model.settings.stylepackage.DocIdentifier;
import ch.sbb.polarion.extension.docx_exporter.rest.model.settings.stylepackage.StylePackageWeightInfo;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;

import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Secured
@Path("/api")
public class SettingsApiController extends SettingsInternalController {

    private static final PolarionService polarionService = new PolarionService();

    @Override
    public Response downloadTranslations(String name, String language, String revision, String scope) {
        return polarionService.callPrivileged(() -> super.downloadTranslations(name, language, revision, scope));
    }

    @Override
    public Map<String, String> uploadTranslations(FormDataBodyPart file, String language, String scope) {
        return polarionService.callPrivileged(() -> super.uploadTranslations(file, language, scope));
    }

    @Override
    public Collection<SettingName> getSuitableStylePackageNames(List<DocIdentifier> docIdentifiers) {
        return polarionService.callPrivileged(() -> super.getSuitableStylePackageNames(docIdentifiers));
    }

    @Override
    public Collection<StylePackageWeightInfo> getStylePackageWeights(String scope) {
        return polarionService.callPrivileged(() -> super.getStylePackageWeights(scope));
    }

    @Override
    public void updateStylePackageWeights(List<StylePackageWeightInfo> stylePackageWeights) {
        polarionService.callPrivileged(() -> super.updateStylePackageWeights(stylePackageWeights));
    }
}
