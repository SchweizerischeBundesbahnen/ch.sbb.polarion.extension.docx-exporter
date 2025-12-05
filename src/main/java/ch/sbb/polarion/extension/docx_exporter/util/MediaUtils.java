package ch.sbb.polarion.extension.docx_exporter.util;

import ch.sbb.polarion.extension.generic.regex.RegexMatcher;
import ch.sbb.polarion.extension.generic.util.BundleJarsPrioritizingRunnable;
import com.polarion.core.util.StringUtils;
import com.polarion.core.util.logging.Logger;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

import static ch.sbb.polarion.extension.docx_exporter.util.TikaMimeTypeResolver.PARAM_RESULT;
import static ch.sbb.polarion.extension.docx_exporter.util.TikaMimeTypeResolver.PARAM_VALUE;

@UtilityClass
public class MediaUtils {
    public static final String IMG_SRC_REGEX = "<img[^<>]*src=(\"|')(?<url>[^(\"|')]*)(\"|')";
    public static final String URL_REGEX = "url\\(\\s*([\"'])?(?<url>.*?)\\1?\\s*\\)";
    public static final String RESOURCE_EXTENSION_REGEX = "^.*\\.(?<extension>[a-zA-Z\\d]{3,4})(?:[?&#]|$)";
    public static final String DATA_URL_PREFIX = "data:";
    private static final Logger logger = Logger.getLogger(MediaUtils.class);
    private static final int PDF_TO_PNG_DPI = 300;
    private static final String IMG_FORMAT_PNG = "png";

    private static final Map<String, String> CUSTOM_MIME_TYPES_MAP = Map.of(
            "cur", "image/x-icon",
            "woff", "application/font-woff",
            "ttf", "application/font-ttf"
    );

    @SneakyThrows
    public BufferedImage pdfPageToImage(PDDocument document, int page) {
        return new PDFRenderer(document).renderImageWithDPI(page, PDF_TO_PNG_DPI);
    }

    @SneakyThrows
    public byte[] toPng(BufferedImage image) {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        ImageIO.write(image, IMG_FORMAT_PNG, os);
        return os.toByteArray();
    }

    public String getImageFormat(@NotNull String imagePath) {
        if (imagePath.endsWith(".gif")) {
            return "image/gif";
        } else if (imagePath.endsWith(".png")) {
            return "image/png";
        } else {
            return "image/jpeg";
        }
    }

    public boolean sameImages(BufferedImage referenceImage, BufferedImage imageToCompare) {
        return diffImages(referenceImage, imageToCompare).isEmpty();
    }

    @SuppressWarnings("java:S3776") // ignore cognitive complexity complaint
    public List<Point> diffImages(BufferedImage referenceImage, BufferedImage imageToCompare) {
        List<Point> diffPoints = new ArrayList<>();
        int width = imageToCompare.getWidth();
        int height = imageToCompare.getHeight();
        if (referenceImage.getWidth() != imageToCompare.getWidth() || referenceImage.getHeight() != imageToCompare.getHeight()) {
            // when image size is different we return 1px border
            for (int x = 0; x < width; x++) {
                // Top edge
                diffPoints.add(new Point(x, 0));
                // Bottom edge
                diffPoints.add(new Point(x, height - 1));
            }
            for (int y = 1; y < height - 1; y++) {
                // Left edge
                diffPoints.add(new Point(0, y));
                // Right edge
                diffPoints.add(new Point(width - 1, y));
            }
        } else {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (referenceImage.getRGB(x, y) != imageToCompare.getRGB(x, y)) {
                        diffPoints.add(new Point(x, y));
                    }
                }
            }
        }

        return diffPoints;
    }

    public void fillImagePoints(BufferedImage image, List<Point> pointsToFill, int color) {
        for (Point point : pointsToFill) {
            image.setRGB(point.x, point.y, color);
        }
    }

    /**
     * Check whether particular string is a <a href="https://www.rfc-editor.org/rfc/rfc2397">'data' URL</a>-encoded entry.
     */
    public boolean isDataUrl(@Nullable String resourceUrl) {
        return resourceUrl != null && resourceUrl.startsWith(DATA_URL_PREFIX);
    }

    public String inlineBase64Resources(String content, FileResourceProvider fileResourceProvider) {
        RegexMatcher.IReplacementCalculator dataReplacement = engine -> {
            String url = engine.group("url");
            String base64String = MediaUtils.isDataUrl(url) ? url : fileResourceProvider.getResourceAsBase64String(url);
            return base64String == null ? null : engine.group().replace(url, base64String);
        };

        // replace tags like <img src="...
        String intermediateResult = RegexMatcher.get(IMG_SRC_REGEX).replace(content, dataReplacement);
        // replace CSS parameters like background: src('/polarion/...
        return RegexMatcher.get(URL_REGEX).useJavaUtil().replace(intermediateResult, dataReplacement);
    }

    /**
     * Attempt to guess media type using resource name or its content.
     * <a href="https://www.iana.org/assignments/media-types/media-types.xhtml">More about media types.</a>
     *
     * @param resource      resource name or link address
     * @param resourceBytes content
     * @return media type or null if it's not recognized by given parameters
     */
    @SneakyThrows
    @Nullable
    @SuppressWarnings("squid:S1166") // no need to log or rethrow exception by design
    public String guessMimeType(@NotNull String resource, byte[] resourceBytes) {

        // there are several ways to recognize mime type, so we're going to try them all until positive result
        List<BiFunction<String, byte[], String>> mimeSources = Arrays.asList(
                MediaUtils::getMimeTypeUsingCustomRegex,
                MediaUtils::getMimeTypeUsingTikaByResourceName,
                MediaUtils::getMimeTypeUsingTikaByContent,
                MediaUtils::getMimeTypeUsingFilesProbe,
                MediaUtils::getMimeTypeUsingURLConnection
        );

        for (BiFunction<String, byte[], String> source : mimeSources) {
            try {
                String mimeType = source.apply(resource, resourceBytes);
                if (!StringUtils.isEmpty(mimeType)) {
                    return mimeType;
                }
            } catch (Exception e) {
                // ignore exceptions by design, no need to log their details, just proceed to the next attempt
            }
        }
        logger.error("Cannot get mime type for the resource: " + resource);
        return null;
    }

    private String getMimeTypeUsingCustomRegex(@NotNull String resource, byte[] resourceBytes) {
        return CUSTOM_MIME_TYPES_MAP.get(RegexMatcher.get(RESOURCE_EXTENSION_REGEX).findFirst(resource, engine -> engine.group("extension")).map(String::toLowerCase).orElse(""));
    }

    @SneakyThrows
    private String getMimeTypeUsingFilesProbe(@NotNull String resource, byte[] resourceBytes) {
        return Files.probeContentType(Paths.get(resource));
    }

    @SuppressWarnings("unchecked")
    public String getMimeTypeUsingTikaByResourceName(@NotNull String resource, byte[] resourceBytes) {
        return ((Optional<String>) BundleJarsPrioritizingRunnable.execute(TikaMimeTypeResolver.class, Map.of(PARAM_VALUE, resource), true).get(PARAM_RESULT)).orElse(null);
    }

    @SuppressWarnings("unchecked")
    public String getMimeTypeUsingTikaByContent(@NotNull String resource, byte[] resourceBytes) {
        return ((Optional<String>) BundleJarsPrioritizingRunnable.execute(TikaMimeTypeResolver.class, Map.of(PARAM_VALUE, resourceBytes), true).get(PARAM_RESULT)).orElse(null);
    }

    @SneakyThrows
    private String getMimeTypeUsingURLConnection(@NotNull String resource, byte[] resourceBytes) {
        try (InputStream is = new BufferedInputStream(new ByteArrayInputStream(resourceBytes))) {
            return URLConnection.guessContentTypeFromStream(is);
        }
    }
}
