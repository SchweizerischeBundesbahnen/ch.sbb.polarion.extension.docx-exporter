package ch.sbb.polarion.extension.docx_exporter.util;

import ch.sbb.polarion.extension.generic.util.ScopeUtils;
import com.polarion.core.boot.PolarionProperties;
import com.polarion.core.config.Configuration;
import com.polarion.core.util.StringUtils;
import org.jetbrains.annotations.NotNull;

public class PdfTemplateProcessor {

    private static final String LOCALHOST = "localhost";
    public static final String HTTP_PROTOCOL_PREFIX = "http://";
    public static final String HTTPS_PROTOCOL_PREFIX = "https://";

    @NotNull
    public String processUsing(@NotNull String documentName, @NotNull String content) {
        return ScopeUtils.getFileContent("webapp/docx-exporter/html/pdfTemplate.html")
                .replace("{DOC_NAME}", documentName)
                .replace("{BASE_URL}", buildBaseUrlHeader())
                .replace("{DOC_CONTENT}", content);
    }

    public String buildSizeCss() {
        return " @page {size: A4 %portrait;}";
    }

    public String buildBaseUrlHeader() {
        String baseUrl = getBaseUrl();
        return baseUrl != null ? String.format("<base href='%s' />", baseUrl) : "";
    }

    private String getBaseUrl() {
        String polarionBaseUrl = System.getProperty(PolarionProperties.BASE_URL, LOCALHOST);
        if (!polarionBaseUrl.contains(LOCALHOST)) {
            return enrichByProtocolPrefix(polarionBaseUrl);
        }
        String hostname = Configuration.getInstance().cluster().nodeHostname();
        return enrichByProtocolPrefix(hostname);
    }

    private static String enrichByProtocolPrefix(String hostname) {
        if (StringUtils.isEmpty(hostname) || hostname.startsWith(HTTP_PROTOCOL_PREFIX) || hostname.startsWith(HTTPS_PROTOCOL_PREFIX)) {
            return hostname;
        } else {
            return HTTP_PROTOCOL_PREFIX + hostname;
        }
    }
}
