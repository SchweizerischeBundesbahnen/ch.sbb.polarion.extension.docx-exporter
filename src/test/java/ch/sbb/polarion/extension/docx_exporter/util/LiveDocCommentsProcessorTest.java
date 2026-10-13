package ch.sbb.polarion.extension.docx_exporter.util;

import ch.sbb.polarion.extension.docx_exporter.model.LiveDocComment;
import ch.sbb.polarion.extension.docx_exporter.rest.model.conversion.CommentsRenderType;
import com.polarion.alm.server.api.model.document.ProxyDocument;
import com.polarion.alm.shared.api.model.comment.CommentBase;
import com.polarion.alm.shared.api.model.comment.CommentBasesTreeField;
import com.polarion.alm.shared.api.model.document.UpdatableDocumentFields;
import com.polarion.alm.shared.api.model.user.User;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LiveDocCommentsProcessorTest {

    @Test
    @SuppressWarnings("unchecked")
    void addLiveDocCommentsTest() {
        ProxyDocument document = mock(ProxyDocument.class, RETURNS_DEEP_STUBS);

        UpdatableDocumentFields fields = mock(UpdatableDocumentFields.class);

        CommentBasesTreeField<CommentBase> comments = mock(CommentBasesTreeField.class);
        doCallRealMethod().when(comments).forEach(any(Consumer.class));
        when(fields.comments()).thenReturn(comments);
        when(document.fields()).thenReturn(fields);

        String html = """
                <div>some content1</div>
                <span id="polarion-comment:1"></span>
                <div>some content2</div>
                <span id="polarion-comment:2"></span>
                <div>some content3</div>
                <span id="polarion-comment:3"></span>
                <div>some content4</div>
                """;

        List<CommentBase> commentBases = List.of(
                mockComment("1", "text1", "author1", false),
                mockComment("2", "text2", "author2", true),
                mockComment("3", "text3", "author3", false)
        );

        when(comments.iterator()).thenReturn(commentBases.iterator());

        // resolved comment isn't rendered
        LiveDocCommentsProcessor openProcessor = new LiveDocCommentsProcessor();
        Map<String, LiveDocComment> openComments = openProcessor.getLiveDocComments(document, CommentsRenderType.OPEN);
        assertEquals("""
                <div>some content1</div>
                [span class=comment level-0][span class=meta][span class=date]2025-03-13 16:21[/span][span class=author]author1[/span][/span][span class=text]text1[/span][/span]
                <div>some content2</div>

                <div>some content3</div>
                [span class=comment level-0][span class=meta][span class=date]2025-03-13 16:23[/span][span class=author]author3[/span][/span][span class=text]text3[/span][/span]
                <div>some content4</div>
                """, openProcessor.addLiveDocComments(html, openComments, new HashSet<>()));

        when(comments.iterator()).thenReturn(commentBases.iterator());

        // now resolved comment is here
        LiveDocCommentsProcessor allProcessor = new LiveDocCommentsProcessor();
        Map<String, LiveDocComment> allComments = allProcessor.getLiveDocComments(document, CommentsRenderType.ALL);
        assertEquals("""
                <div>some content1</div>
                [span class=comment level-0][span class=meta][span class=date]2025-03-13 16:21[/span][span class=author]author1[/span][/span][span class=text]text1[/span][/span]
                <div>some content2</div>
                [span class=comment level-0][span class=meta][span class=date]2025-03-13 16:22[/span][span class=status-resolved]Resolved[/span][span class=author]author2[/span][/span][span class=text]text2[/span][/span]
                <div>some content3</div>
                [span class=comment level-0][span class=meta][span class=date]2025-03-13 16:23[/span][span class=author]author3[/span][/span][span class=text]text3[/span][/span]
                <div>some content4</div>
                """, allProcessor.addLiveDocComments(html, allComments, new HashSet<>()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void addLiveDocCommentsPopulatesRenderedCommentIds() {
        ProxyDocument document = mock(ProxyDocument.class, RETURNS_DEEP_STUBS);

        UpdatableDocumentFields fields = mock(UpdatableDocumentFields.class);
        CommentBasesTreeField<CommentBase> comments = mock(CommentBasesTreeField.class);
        doCallRealMethod().when(comments).forEach(any(Consumer.class));
        when(fields.comments()).thenReturn(comments);
        when(document.fields()).thenReturn(fields);

        String html = """
                <div>content</div>
                <span id="polarion-comment:1"></span>
                <div>more</div>
                <img id="polarion-comment:3" class="polarion-dle-comment-icon"/>
                """;

        List<CommentBase> commentBases = List.of(
                mockComment("1", "text1", "author1", false),
                mockComment("3", "text3", "author3", false)
        );
        when(comments.iterator()).thenReturn(commentBases.iterator());

        Set<String> renderedCommentIds = new HashSet<>();
        LiveDocCommentsProcessor processor = new LiveDocCommentsProcessor();
        Map<String, LiveDocComment> liveDocComments = processor.getLiveDocComments(document, CommentsRenderType.OPEN);
        processor.addLiveDocComments(html, liveDocComments, renderedCommentIds);

        assertEquals(Set.of("1", "3"), renderedCommentIds);
    }

    /** Polarion renders the icon of a comment which refers to a referenced work item after its description. */
    @Test
    @SuppressWarnings("unchecked")
    void placesTheCommentOfAReferencedWorkItemAfterItsDescription() {
        ProxyDocument document = mock(ProxyDocument.class, RETURNS_DEEP_STUBS);

        UpdatableDocumentFields fields = mock(UpdatableDocumentFields.class);
        CommentBasesTreeField<CommentBase> comments = mock(CommentBasesTreeField.class);
        doCallRealMethod().when(comments).forEach(any(Consumer.class));
        when(fields.comments()).thenReturn(comments);
        when(document.fields()).thenReturn(fields);

        String html = """
                <div id="polarion_wiki macro name=module-workitem;params=id=EL-343|external=true" class="polarion-dle-workitem-basic-0 polarion-dle-workitem-basic-external">
                <p>Some random workitem</p><img id="polarion-comment:4" title="text4" contenteditable="false" src="/polarion/ria/images/control/comment.png" class="polarion-dle-comment-icon"/>
                </div>
                """;
        List<CommentBase> commentBases = List.of(mockComment("4", "text4", "author4", false));
        when(comments.iterator()).thenReturn(commentBases.iterator());

        Set<String> renderedCommentIds = new HashSet<>();
        LiveDocCommentsProcessor processor = new LiveDocCommentsProcessor();
        Map<String, LiveDocComment> liveDocComments = processor.getLiveDocComments(document, CommentsRenderType.OPEN);
        String result = processor.addLiveDocComments(html, liveDocComments, renderedCommentIds);

        assertEquals("""
                <div id="polarion_wiki macro name=module-workitem;params=id=EL-343|external=true" class="polarion-dle-workitem-basic-0 polarion-dle-workitem-basic-external">
                <p>Some random workitem</p>[span class=comment level-0][span class=meta][span class=date]2025-03-13 16:24[/span][span class=author]author4[/span][/span][span class=text]text4[/span][/span]
                </div>
                """, result);
        assertEquals(result, processor.addUnreferencedComments(result, liveDocComments, renderedCommentIds), "The comment is not left for the unreferenced ones");
    }

    @Test
    @SuppressWarnings("unchecked")
    void placesTheCommentWithSeveralParagraphsAtItsIcon() {
        ProxyDocument document = mock(ProxyDocument.class, RETURNS_DEEP_STUBS);

        UpdatableDocumentFields fields = mock(UpdatableDocumentFields.class);
        CommentBasesTreeField<CommentBase> comments = mock(CommentBasesTreeField.class);
        doCallRealMethod().when(comments).forEach(any(Consumer.class));
        when(fields.comments()).thenReturn(comments);
        when(document.fields()).thenReturn(fields);

        // Polarion escapes the plain text of the comment into the title, which turns a line break into a raw <br/>
        String html = """
                <p>Optional values<img id="polarion-comment:5" title="Major: first paragraph?<br/><br/>Second &quot;paragraph&quot;" \
                contenteditable="false" src="/polarion/ria/images/control/comment_resolved.png" class="polarion-dle-comment-resolved-icon"/></p>
                """;
        List<CommentBase> commentBases = List.of(mockComment("5", "text5", "author5", false));
        when(comments.iterator()).thenReturn(commentBases.iterator());

        Set<String> renderedCommentIds = new HashSet<>();
        LiveDocCommentsProcessor processor = new LiveDocCommentsProcessor();
        Map<String, LiveDocComment> liveDocComments = processor.getLiveDocComments(document, CommentsRenderType.ALL);
        String result = processor.addLiveDocComments(html, liveDocComments, renderedCommentIds);

        assertEquals("""
                <p>Optional values[span class=comment level-0][span class=meta][span class=date]2025-03-13 16:25[/span][span class=author]author5[/span][/span][span class=text]text5[/span][/span]</p>
                """, result);
        assertEquals(Set.of("5"), renderedCommentIds);
    }

    @Test
    @SuppressWarnings("unchecked")
    void addUnreferencedCommentsAppendsWhenUnreferencedExist() {
        ProxyDocument document = mock(ProxyDocument.class, RETURNS_DEEP_STUBS);

        UpdatableDocumentFields fields = mock(UpdatableDocumentFields.class);
        CommentBasesTreeField<CommentBase> comments = mock(CommentBasesTreeField.class);
        doCallRealMethod().when(comments).forEach(any(Consumer.class));
        when(fields.comments()).thenReturn(comments);
        when(document.fields()).thenReturn(fields);

        List<CommentBase> commentBases = List.of(
                mockComment("1", "text1", "author1", false),
                mockComment("2", "text2", "author2", false),
                mockComment("3", "text3", "author3", false)
        );
        when(comments.iterator()).thenReturn(commentBases.iterator());

        Set<String> renderedCommentIds = new HashSet<>(Set.of("1"));
        String html = "<div>content</div>";

        LiveDocCommentsProcessor processor = new LiveDocCommentsProcessor();
        Map<String, LiveDocComment> liveDocComments = processor.getLiveDocComments(document, CommentsRenderType.OPEN);
        String result = processor.addUnreferencedComments(html, liveDocComments, renderedCommentIds);

        assertTrue(result.startsWith("<div>content</div>"));
        assertTrue(result.contains("[span class=unreferenced-comments-delimiter][/span]"));
        assertTrue(result.contains("text2"));
        assertTrue(result.contains("text3"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void addUnreferencedCommentsReturnsOriginalWhenAllReferenced() {
        ProxyDocument document = mock(ProxyDocument.class, RETURNS_DEEP_STUBS);

        UpdatableDocumentFields fields = mock(UpdatableDocumentFields.class);
        CommentBasesTreeField<CommentBase> comments = mock(CommentBasesTreeField.class);
        doCallRealMethod().when(comments).forEach(any(Consumer.class));
        when(fields.comments()).thenReturn(comments);
        when(document.fields()).thenReturn(fields);

        List<CommentBase> commentBases = List.of(
                mockComment("1", "text1", "author1", false)
        );
        when(comments.iterator()).thenReturn(commentBases.iterator());

        Set<String> renderedCommentIds = new HashSet<>(Set.of("1"));
        String html = "<div>content</div>";

        LiveDocCommentsProcessor processor = new LiveDocCommentsProcessor();
        Map<String, LiveDocComment> liveDocComments = processor.getLiveDocComments(document, CommentsRenderType.OPEN);
        String result = processor.addUnreferencedComments(html, liveDocComments, renderedCommentIds);

        assertEquals(html, result);
    }

    @Test
    @SuppressWarnings("unchecked")
    void addUnreferencedCommentsRespectsOpenPreference() {
        ProxyDocument document = mock(ProxyDocument.class, RETURNS_DEEP_STUBS);

        UpdatableDocumentFields fields = mock(UpdatableDocumentFields.class);
        CommentBasesTreeField<CommentBase> comments = mock(CommentBasesTreeField.class);
        doCallRealMethod().when(comments).forEach(any(Consumer.class));
        when(fields.comments()).thenReturn(comments);
        when(document.fields()).thenReturn(fields);

        List<CommentBase> commentBases = List.of(
                mockComment("1", "text1", "author1", false),
                mockComment("2", "text2-resolved", "author2", true)
        );
        when(comments.iterator()).thenReturn(commentBases.iterator());

        LiveDocCommentsProcessor processor = new LiveDocCommentsProcessor();
        Map<String, LiveDocComment> liveDocComments = processor.getLiveDocComments(document, CommentsRenderType.OPEN);
        String result = processor.addUnreferencedComments("<div>content</div>", liveDocComments, new HashSet<>());

        assertTrue(result.contains("text1"));
        // OPEN preference must exclude the resolved unreferenced comment
        assertFalse(result.contains("text2-resolved"));
    }

    @Test
    void addUnreferencedCommentsReturnsOriginalWhenNullRenderType() {
        ProxyDocument document = mock(ProxyDocument.class, RETURNS_DEEP_STUBS);

        Set<String> renderedCommentIds = new HashSet<>();
        String html = "<div>content</div>";

        LiveDocCommentsProcessor processor = new LiveDocCommentsProcessor();
        Map<String, LiveDocComment> liveDocComments = processor.getLiveDocComments(document, null);
        String result = processor.addUnreferencedComments(html, liveDocComments, renderedCommentIds);

        assertEquals(html, result);
    }

    @Test
    @SuppressWarnings("unchecked")
    void addLiveDocCommentsRendersUnresolvableUserLabel() {
        ProxyDocument document = mock(ProxyDocument.class, RETURNS_DEEP_STUBS);

        UpdatableDocumentFields fields = mock(UpdatableDocumentFields.class);
        CommentBasesTreeField<CommentBase> comments = mock(CommentBasesTreeField.class);
        doCallRealMethod().when(comments).forEach(any(Consumer.class));
        when(fields.comments()).thenReturn(comments);
        when(document.fields()).thenReturn(fields);

        CommentBase commentBase = mockComment("1", "text1", "irrelevant-name", false);
        User user = Objects.requireNonNull(commentBase.fields().author().get());
        when(user.isUnresolvable()).thenReturn(true);
        when(user.label()).thenReturn("deleted-user-label");
        when(comments.iterator()).thenReturn(List.of(commentBase).iterator());

        String html = """
                <div>some content</div>
                <span id="polarion-comment:1"></span>
                """;

        LiveDocCommentsProcessor processor = new LiveDocCommentsProcessor();
        Map<String, LiveDocComment> liveDocComments = processor.getLiveDocComments(document, CommentsRenderType.ALL);
        String result = processor.addLiveDocComments(html, liveDocComments, new HashSet<>());

        assertTrue(result.contains("[span class=author]deleted-user-label[/span]"));
        assertFalse(result.contains("irrelevant-name"));
    }

    @SneakyThrows
    private CommentBase mockComment(String id, String text, String author, boolean resolved) {
        CommentBase comment = mock(CommentBase.class, RETURNS_DEEP_STUBS);
        lenient().when(comment.fields().parentComment().get()).thenReturn(null);
        lenient().when(comment.fields().id().get()).thenReturn(id);
        lenient().when(comment.fields().created().get()).thenReturn(new SimpleDateFormat("yyyy-MM-dd HH:mm").parse("2025-03-13 16:2%s".formatted(id)));
        lenient().when(comment.fields().text().persistedHtml()).thenReturn(text);
        lenient().when(Objects.requireNonNull(comment.fields().author().get()).fields().name().get()).thenReturn(author);
        when(comment.fields().resolved().get()).thenReturn(resolved);
        return comment;
    }
}
