package ch.sbb.polarion.extension.docx_exporter.pandoc;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.MountableFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Renders the pages of a PDF into images inside a container of a pinned JDK. Java2D draws some edges with other pixels
 * on another OS, JDK or architecture, so the reference images hold only for the JVM that rendered them. Without Docker
 * the tests render on the host, and a reference can then fail on pixels alone.
 */
public final class PageRendererContainer {

    private static final Logger logger = LoggerFactory.getLogger(PageRendererContainer.class);
    /** The JDK and OS the reference images come from, as the runners of CI had them. */
    static final String DOCKER_IMAGE_NAME = "eclipse-temurin:21.0.13_9-jdk@sha256:ebfbb18d11ff163ee83b21258acf12248af48d22a0fdfdc68fa1a1516c05976a";
    private static final String LIB_FOLDER = "/renderer/lib/";
    private static final String CLASSES_FOLDER = "/renderer/classes/";
    private static final String WORK_FOLDER = "/tmp/";
    /** One class of each jar PDFBox renders with. By name: a class this file referenced would count as a dependency of its own for dependency:analyze. */
    private static final List<String> PDFBOX_JAR_CLASSES = List.of(
            "org.apache.pdfbox.pdmodel.PDDocument", "org.apache.pdfbox.io.RandomAccessRead", "org.apache.fontbox.ttf.TrueTypeFont", "org.apache.commons.logging.LogFactory");

    private PageRendererContainer() {
    }

    private static class ContainerHolder {
        private static final @Nullable GenericContainer<?> INSTANCE = startContainer();
        private static final @Nullable String ARCHITECTURE = INSTANCE != null ? readArchitecture(INSTANCE) : null;

        @SuppressWarnings("resource") // stopped by Testcontainers when the JVM exits
        private static @Nullable GenericContainer<?> startContainer() {
            if (!DockerClientFactory.instance().isDockerAvailable()) {
                logger.warn("Docker is not available: the PDF pages are rendered on the host, and the reference images can differ in pixels");
                return null;
            }
            try {
                GenericContainer<?> container = new GenericContainer<>(DOCKER_IMAGE_NAME).withCommand("sleep", "infinity");
                for (String pdfboxClass : PDFBOX_JAR_CLASSES) {
                    Path jar = codeSource(Class.forName(pdfboxClass));
                    container.withCopyFileToContainer(MountableFile.forHostPath(jar), LIB_FOLDER + jar.getFileName());
                }
                String rendererClassFile = PdfPageRenderer.class.getName().replace('.', '/') + ".class";
                container.withCopyFileToContainer(MountableFile.forHostPath(codeSource(PdfPageRenderer.class).resolve(rendererClassFile)), CLASSES_FOLDER + rendererClassFile);
                container.start();
                logger.info("Page renderer container started from {}", DOCKER_IMAGE_NAME);
                return container;
            } catch (Exception e) {
                logger.warn("The page renderer container did not start: the PDF pages are rendered on the host, and the reference images can differ in pixels", e);
                return null;
            }
        }

        private static @Nullable String readArchitecture(@NotNull GenericContainer<?> container) {
            try {
                return container.execInContainer("uname", "-m").getStdout().trim();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }

        private static @NotNull Path codeSource(@NotNull Class<?> clazz) throws URISyntaxException {
            return Path.of(clazz.getProtectionDomain().getCodeSource().getLocation().toURI());
        }
    }

    public static boolean isAvailable() {
        return ContainerHolder.INSTANCE != null;
    }

    /**
     * The architecture the container runs on, as {@code uname -m} reports it, or {@code null} without the container.
     */
    public static @Nullable String getArchitecture() {
        return ContainerHolder.ARCHITECTURE;
    }

    public static @NotNull List<BufferedImage> renderPages(byte @NotNull [] pdf, int pageCount, float dpi) throws IOException {
        GenericContainer<?> container = ContainerHolder.INSTANCE;
        if (container == null) {
            throw new IllegalStateException("The page renderer container is not available");
        }
        String name = WORK_FOLDER + UUID.randomUUID();
        container.copyFileToContainer(Transferable.of(pdf), name + ".pdf");
        try {
            Container.ExecResult result = container.execInContainer("java", "-Djava.awt.headless=true", "-cp", LIB_FOLDER + "*:" + CLASSES_FOLDER,
                    PdfPageRenderer.class.getName(), name + ".pdf", name + "_", String.valueOf(dpi));
            if (result.getExitCode() != 0) {
                throw new IOException("Rendering the PDF pages failed: " + result.getStderr());
            }
            List<BufferedImage> images = new ArrayList<>();
            for (int page = 0; page < pageCount; page++) {
                images.add(container.copyFileFromContainer(name + "_" + page + ".png", ImageIO::read));
            }
            container.execInContainer("sh", "-c", "rm -f " + name + "*");
            return images;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }
}
