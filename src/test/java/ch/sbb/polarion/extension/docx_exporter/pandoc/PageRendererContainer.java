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

    private record Renderer(@NotNull GenericContainer<?> container, @NotNull String architecture) {
    }

    private static class ContainerHolder {
        private static final @Nullable Renderer INSTANCE = startRenderer();

        private static @Nullable Renderer startRenderer() {
            if (!DockerClientFactory.instance().isDockerAvailable()) {
                logger.warn("Docker is not available: the PDF pages are rendered on the host, and the reference images can differ in pixels");
                return null;
            }
            GenericContainer<?> container = new GenericContainer<>(DOCKER_IMAGE_NAME).withCommand("sleep", "infinity");
            try {
                for (String pdfboxClass : PDFBOX_JAR_CLASSES) {
                    Path jar = codeSource(Class.forName(pdfboxClass));
                    container.withCopyFileToContainer(MountableFile.forHostPath(jar), LIB_FOLDER + jar.getFileName());
                }
                String rendererClassFile = PdfPageRenderer.class.getName().replace('.', '/') + ".class";
                container.withCopyFileToContainer(MountableFile.forHostPath(codeSource(PdfPageRenderer.class).resolve(rendererClassFile)), CLASSES_FOLDER + rendererClassFile);
                container.start();
                // Read here: a failure in a static initializer would fail every later test instead of falling back to the host
                Container.ExecResult uname = container.execInContainer("uname", "-m");
                String architecture = uname.getStdout().trim();
                if (uname.getExitCode() != 0 || architecture.isEmpty()) {
                    throw new IOException("uname -m failed: " + uname.getStderr());
                }
                logger.info("Page renderer container started from {} on {}", DOCKER_IMAGE_NAME, architecture);
                return new Renderer(container, architecture);
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                logger.warn("The page renderer container did not start: the PDF pages are rendered on the host, and the reference images can differ in pixels", e);
                container.stop();
                return null;
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
        Renderer renderer = ContainerHolder.INSTANCE;
        return renderer != null ? renderer.architecture() : null;
    }

    public static @NotNull List<BufferedImage> renderPages(byte @NotNull [] pdf, int pageCount, float dpi) throws IOException {
        Renderer renderer = ContainerHolder.INSTANCE;
        if (renderer == null) {
            throw new IllegalStateException("The page renderer container is not available");
        }
        GenericContainer<?> container = renderer.container();
        String name = WORK_FOLDER + UUID.randomUUID();
        try {
            container.copyFileToContainer(Transferable.of(pdf), name + ".pdf");
            Container.ExecResult result = container.execInContainer("java", "-Djava.awt.headless=true", "-cp", LIB_FOLDER + "*:" + CLASSES_FOLDER,
                    PdfPageRenderer.class.getName(), name + ".pdf", name + "_", String.valueOf(dpi));
            if (result.getExitCode() != 0) {
                throw new IOException("Rendering the PDF pages failed: " + result.getStderr());
            }
            List<BufferedImage> images = new ArrayList<>();
            for (int page = 0; page < pageCount; page++) {
                images.add(container.copyFileFromContainer(name + "_" + page + ".png", ImageIO::read));
            }
            return images;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        } finally {
            removeFiles(container, name);
        }
    }

    /** Leaves the error of the render, if there is one, to the caller. */
    private static void removeFiles(@NotNull GenericContainer<?> container, @NotNull String name) {
        try {
            container.execInContainer("sh", "-c", "rm -f " + name + "*");
        } catch (IOException e) {
            logger.warn("Could not remove {}* from the page renderer container", name, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
