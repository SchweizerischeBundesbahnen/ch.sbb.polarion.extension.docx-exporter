package ch.sbb.polarion.extension.docx_exporter.util.velocity;

import com.polarion.alm.wiki.IWikiService;
import com.polarion.platform.core.IPlatform;
import com.polarion.platform.core.PlatformContext;
import org.apache.velocity.VelocityContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VelocityEvaluatorTest {

    private VelocityEvaluator velocityEvaluator;

    @BeforeEach
    void setUp() {
        velocityEvaluator = new VelocityEvaluator();
    }

    @Test
    void addWikiRenderingContextAddsWikiTools() {
        IWikiService wikiService = mock(IWikiService.class);
        Object calendarTool = new Object();
        when(wikiService.getWikiRenderingContextMap()).thenReturn(Map.of("calendarTool", calendarTool));

        VelocityContext velocityContext = new VelocityContext();
        withWikiService(wikiService, () -> velocityEvaluator.addWikiRenderingContext(velocityContext));

        assertEquals(calendarTool, velocityContext.get("calendarTool"));
    }

    @Test
    void addWikiRenderingContextKeepsExistingValues() {
        IWikiService wikiService = mock(IWikiService.class);
        when(wikiService.getWikiRenderingContextMap()).thenReturn(Map.of("trackerService", new Object()));

        Object richPageTrackerService = new Object();
        VelocityContext velocityContext = new VelocityContext();
        velocityContext.put("trackerService", richPageTrackerService);
        withWikiService(wikiService, () -> velocityEvaluator.addWikiRenderingContext(velocityContext));

        assertEquals(richPageTrackerService, velocityContext.get("trackerService"));
    }

    @Test
    void addWikiRenderingContextSkippedWhenNoWikiService() {
        VelocityContext velocityContext = new VelocityContext();
        withWikiService(null, () -> velocityEvaluator.addWikiRenderingContext(velocityContext));

        assertNull(velocityContext.get("calendarTool"));
    }

    @Test
    void addWikiRenderingContextDoesNotUseContainsKey() {
        IWikiService wikiService = mock(IWikiService.class);
        when(wikiService.getWikiRenderingContextMap()).thenReturn(Map.of("calendarTool", new Object()));

        VelocityContext velocityContext = spy(new VelocityContext());
        withWikiService(wikiService, () -> velocityEvaluator.addWikiRenderingContext(velocityContext));

        verify(velocityContext, never()).containsKey(any());
    }

    private void withWikiService(IWikiService wikiService, Runnable runnable) {
        IPlatform platform = mock(IPlatform.class);
        when(platform.lookupService(IWikiService.class)).thenReturn(wikiService);
        try (MockedStatic<PlatformContext> platformContextMockedStatic = mockStatic(PlatformContext.class)) {
            platformContextMockedStatic.when(PlatformContext::getPlatform).thenReturn(platform);
            runnable.run();
        }
    }
}
