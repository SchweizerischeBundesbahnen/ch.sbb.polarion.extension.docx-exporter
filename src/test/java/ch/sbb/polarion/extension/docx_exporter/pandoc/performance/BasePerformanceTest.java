package ch.sbb.polarion.extension.docx_exporter.pandoc.performance;

import ch.sbb.polarion.extension.docx_exporter.pandoc.BaseDocxConverterTest;
import ch.sbb.polarion.extension.docx_exporter.rest.model.conversion.ExportParams;
import ch.sbb.polarion.extension.docx_exporter.rest.model.documents.DocumentData;
import ch.sbb.polarion.extension.docx_exporter.rest.model.documents.id.LiveDocId;
import ch.sbb.polarion.extension.docx_exporter.rest.model.settings.templates.TemplatesModel;
import ch.sbb.polarion.extension.docx_exporter.settings.TemplatesSettings;
import ch.sbb.polarion.extension.docx_exporter.util.DocumentDataFactory;
import ch.sbb.polarion.extension.docx_exporter.util.DocxGenerationLog;
import ch.sbb.polarion.extension.generic.test_extensions.BundleJarsPrioritizingRunnableMockExtension;
import ch.sbb.polarion.extension.generic.util.ExecutionProfiler;
import com.polarion.alm.tracker.model.IModule;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

/**
 * The base of the performance tests: they export documents of a known shape and fail when an export takes longer than
 * it does today. They run in the profile {@code performance-tests-with-pandoc-docker} alone.
 * <p>
 * The exporter and pandoc are timed apart, read from the timings of the generation log, so a failure says which side
 * became slow. Each document is exported {@link #RUNS} times, and the time of each part is the average. Each part has a
 * reference time in {@code performance/reference-times-<architecture>.properties}, its average over runs on a machine of
 * that architecture. {@link PerformanceRun} expects each part to take its reference time scaled by how the small document
 * went in this run, which absorbs how fast this machine and this moment are; the small document itself it scales by a
 * fixed piece of JDK work, so that a change which slows every export still shows. It writes a report of every export
 * after the last test.
 * </p>
 * <p>
 * Every export goes through the reference template, as an export in Polarion goes through a template.
 * </p>
 */
@Tag("performance")
// The template is processed by a BundleJarsPrioritizingRunnable, which needs the bundle of a running Polarion otherwise
@ExtendWith({PerformanceRun.Extension.class, BundleJarsPrioritizingRunnableMockExtension.class})
// The test of the small document takes the timing of the baseline and exports nothing, leaving the stubs of its set-up unused
@MockitoSettings(strictness = Strictness.LENIENT)
public abstract class BasePerformanceTest extends BaseDocxConverterTest {

    private static final String PANDOC_STAGE = "Pandoc conversion";
    private static final String TEMPLATE_NAME = "Reference template";

    /** How many times each document is exported, the time of a part being the average of all. */
    protected static final int RUNS = 3;

    protected static final String SMALL_DOCUMENT_TITLE = "Small document";

    /** The small document as the run timed it first, after warming the JVM and the service up. */
    private static Timing baseline;

    /** An export and how long its parts took. */
    protected record Timing(@NotNull String name, byte @NotNull [] docx, long totalMs, long pandocMs, @NotNull String report) {
        long exporterMs() {
            return totalMs - pandocMs;
        }
    }

    protected static @NotNull ExportParams.ExportParamsBuilder portraitA4() {
        return ExportParams.builder()
                .projectId("test")
                .locationPath("testLocation")
                .orientation("PORTRAIT")
                .paperSize("A4")
                .template(TEMPLATE_NAME);
    }

    /**
     * Exports the content as a LiveDoc {@link #RUNS} times and returns the average time of each part: one export of a
     * document takes too little for one run to tell a slower exporter from a busy machine. The timing report of each
     * export is written to the reports folder at once, so that it is there whichever check fails.
     * <p>
     * Before the first export of a run of the tests, {@link #RUNS} exports which are not timed warm the JVM and the service
     * up, and the small document is timed as the baseline of the run, which {@link PerformanceRun} scales the other
     * reference times by. Its own test takes that same timing rather than one of its own: the reference times hold each
     * export against the small document of its run, so the run must scale by the very sample it reports.
     * </p>
     */
    protected @NotNull Timing export(@NotNull String name, @NotNull String title, @NotNull String content, @NotNull ExportParams params) {
        timeTheBaseline();
        return PerformanceRun.SMALL_DOCUMENT.equals(name) ? baseline : average(name, title, content, params);
    }

    /** Warms the JVM and the service up and times the small document, once for the run, before the first export is timed. */
    protected void timeTheBaseline() {
        if (baseline == null) {
            for (int run = 1; run <= RUNS; run++) {
                exportOnce("warmup-" + run, "Warm-up", smallDocument(), portraitA4().build());
            }
            baseline = average(PerformanceRun.SMALL_DOCUMENT, SMALL_DOCUMENT_TITLE, smallDocument(), portraitA4().build());
            PerformanceRun.current().baseline(baseline.exporterMs(), baseline.pandocMs());
        }
    }

    /** The small document, which takes the exporter and pandoc little but what every export costs. */
    @SneakyThrows
    protected static @NotNull String smallDocument() {
        return readHtmlResource("performance/reference");
    }

    private @NotNull Timing average(@NotNull String name, @NotNull String title, @NotNull String content, @NotNull ExportParams params) {
        long totalMs = 0;
        long pandocMs = 0;
        Timing last = null;
        for (int run = 1; run <= RUNS; run++) {
            last = exportOnce(name + "-" + run, title, content, params);
            totalMs += last.totalMs();
            pandocMs += last.pandocMs();
        }
        return new Timing(name, last.docx(), Math.round((double) totalMs / RUNS), Math.round((double) pandocMs / RUNS), last.report());
    }

    @SneakyThrows
    private @NotNull Timing exportOnce(@NotNull String name, @NotNull String title, @NotNull String content, @NotNull ExportParams params) {
        DocumentData<IModule> liveDoc = DocumentData.creator(module)
                .id(LiveDocId.from("testProjectId", "_default", "testDocumentId"))
                .title(title)
                .content(content)
                .lastRevision("42")
                .revisionPlaceholder("42")
                .build();
        documentDataFactoryMockedStatic.when(() -> DocumentDataFactory.getDocumentData(eq(params), anyBoolean())).thenReturn(liveDoc);
        byte[] template = readTemplate("reference_template");
        DocxGenerationLog log = new DocxGenerationLog();
        byte[] docx;
        try (MockedConstruction<TemplatesSettings> ignored = mockConstruction(TemplatesSettings.class,
                (settings, context) -> when(settings.load(any(), any())).thenReturn(TemplatesModel.builder().template(template).build()))) {
            docx = converter.convertToDocx(params, log);
        }
        long pandocMs = log.getTimingEntries().stream()
                .filter(entry -> PANDOC_STAGE.equals(entry.stageName()))
                .mapToLong(ExecutionProfiler.TimingEntry::durationMs)
                .sum();
        Timing timing = new Timing(name, docx, log.getTotalDurationMs(), pandocMs, log.generateTimingReport(title));
        writeReport(timing.name(), "%s: exporter %d ms, pandoc %d ms%n%s".formatted(name, timing.exporterMs(), timing.pandocMs(), timing.report()));
        return timing;
    }

    @SneakyThrows
    protected static void writeReport(@NotNull String name, @NotNull String text) {
        Files.writeString(Path.of(REPORTS_FOLDER_PATH, "performance-" + name + ".txt"), text, StandardCharsets.UTF_8);
    }

    /**
     * Fails when a part of the export took longer than its limit, a multiple of the time {@link PerformanceRun} expects
     * of it on this machine, and only marks it in the report above its warning level. Both parts go into the report of
     * the run first, and the timing report of the export is written to the reports folder either way.
     */
    protected void assertWithinReference(@NotNull Timing timing) {
        PerformanceRun run = PerformanceRun.current();
        run.add(timing.name(), PerformanceRun.EXPORTER, timing.exporterMs());
        run.add(timing.name(), PerformanceRun.PANDOC, timing.pandocMs());
        long exporterLimit = run.limit(timing.name(), PerformanceRun.EXPORTER);
        long pandocLimit = run.limit(timing.name(), PerformanceRun.PANDOC);
        String summary = "%s: exporter %d ms of %d, pandoc %d ms of %d, average of %d exports".formatted(
                timing.name(), timing.exporterMs(), exporterLimit, timing.pandocMs(), pandocLimit, RUNS);
        writeReport(timing.name(), summary + System.lineSeparator() + timing.report());
        assertThat(timing.exporterMs()).as("The exporter is within its limit. %s%n%s", summary, timing.report()).isLessThanOrEqualTo(exporterLimit);
        assertThat(timing.pandocMs()).as("pandoc is within its limit. %s%n%s", summary, timing.report()).isLessThanOrEqualTo(pandocLimit);
    }

    /** How many times an element of the body of the DOCX occurs, as {@code tbl} for its tables or {@code drawing} for its images. */
    @SneakyThrows
    protected static int count(byte @NotNull [] docx, @NotNull String element) {
        String body = documentXml(docx);
        Matcher matcher = Pattern.compile("<w:" + element + "[ >/]").matcher(body);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    @SneakyThrows
    private static @NotNull String documentXml(byte @NotNull [] docx) {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(docx))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if ("word/document.xml".equals(entry.getName())) {
                    return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new IllegalStateException("The DOCX has no word/document.xml");
    }
}
