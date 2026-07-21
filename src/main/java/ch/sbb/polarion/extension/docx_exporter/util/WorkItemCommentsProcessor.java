package ch.sbb.polarion.extension.docx_exporter.util;


import ch.sbb.polarion.extension.docx_exporter.model.WorkItemComment;
import com.polarion.alm.server.api.model.document.ProxyDocument;
import com.polarion.alm.tracker.model.IComment;
import com.polarion.alm.tracker.model.IWorkItem;
import lombok.Builder;
import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WorkItemCommentsProcessor {

    private static final String WORK_ITEM_ID_MARKER = "params=id=";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    @NotNull
    public String addWorkItemComments(ProxyDocument document, @NotNull String html, boolean onlyOpen) {
        final Map<String, Map<String, WorkItemComment>> workItemCommentMap = getCommentsFromWorkItem(document, onlyOpen);
        org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(html);
        for (org.jsoup.nodes.Element el : doc.select("[id*='params=id=']")) {
            String workItemId = extractWorkItemId(el.attr("id"));
            if (workItemId != null) {
                Map<String, WorkItemComment> commentMap = workItemCommentMap.get(workItemId);
                if (commentMap != null && !commentMap.isEmpty()) {
                    StringBuilder rendered = new StringBuilder();
                    for (WorkItemComment comment : commentMap.values()) {
                        rendered.append(renderComment(comment, 0));
                    }
                    el.after(org.jsoup.parser.Parser.unescapeEntities(rendered.toString(), true) + el.html());
                }
            }
        }
        return doc.body().html();
    }

    @VisibleForTesting
    String extractWorkItemId(@NotNull String idAttr) {
        int idx = idAttr.indexOf(WORK_ITEM_ID_MARKER);
        if (idx == -1) {
            return null;
        }
        int start = idx + WORK_ITEM_ID_MARKER.length();
        int end = idAttr.indexOf(';', start);
        return idAttr.substring(start, end == -1 ? idAttr.length() : end);
    }

    private Map<String, Map<String, WorkItemComment>> getCommentsFromWorkItem(@NotNull ProxyDocument document, boolean onlyOpen) {
        Map<String, Map<String, WorkItemComment>> comments = new HashMap<>();
        List<IWorkItem> workItems = document.getOldApi().getAllWorkItems();
        for (IWorkItem workItem : workItems) {
            if (workItem.isUnresolvable()) {
                continue;
            }
            Map<String, WorkItemComment> commentMap = new HashMap<>();
            for (IComment comment : workItem.getRootComments(!onlyOpen)) {
                WorkItemComment wiComment = getCommentFromWorkItem(comment);
                commentMap.put(comment.getId(), wiComment);
            }
            if (!commentMap.isEmpty()) {
                comments.put(workItem.getId(), commentMap);
            }
        }
        return comments;
    }

    private void setChildComments(@NotNull WorkItemComment comment, @NotNull IComment commentBaseFields) {
        if (!commentBaseFields.getChildComments().isEmpty()) {
            Map<String, WorkItemComment> map = new HashMap<>();
            for (IComment child : commentBaseFields.getChildComments()) {
                WorkItemComment childComment = getCommentFromWorkItem(child);
                map.put(childComment.getId(), childComment);
            }
            comment.setChildComments(map);
        }
    }

    private WorkItemComment getCommentFromWorkItem(@NotNull IComment comment) {
        WorkItemComment build = WorkItemComment.builder()
                .title(comment.getTitle())
                .id(comment.getId())
                .text(comment.getText() == null ? comment.getTitle() : comment.getText().getContent())
                .project(comment.getProject().getId())
                .author(comment.getAuthor().getName())
                .created(comment.getCreated().toInstant())
                .resolved(comment.isResolved())
                .build();
        setChildComments(build, comment);
        return build;
    }

    private String renderComment(WorkItemComment workItemComment, int nestingLevel) {
        WorkItemCommentsProcessor.CommentData commentData = getCommentData(workItemComment);
        StringBuilder commentSpan = new StringBuilder(getCommentSpan(commentData, nestingLevel));
        Map<String, WorkItemComment> childComments = workItemComment.getChildComments();
        if (childComments != null) {
            nestingLevel++;
            for (WorkItemComment childComment : childComments.values()) {
                commentSpan.append(renderComment(childComment, nestingLevel));
            }
        }
        return commentSpan.toString();
    }

    private String getCommentSpan(WorkItemCommentsProcessor.CommentData commentData, int nestingLevel) {
        String dateSpan = String.format("[span class=date]%s[/span]", commentData.getDate());
        String statusSpan = commentData.isResolved() ? "[span class=status-resolved]Resolved[/span]" : "";
        String authorSpan = commentData.getAuthor() != null ? String.format("[span class=author]%s[/span]", commentData.getAuthor()) : "";
        String textSpan = String.format("[span class=text]%s[/span]", commentData.getText());
        return String.format("[span class=comment level-%d][span class=meta]%s%s%s[/span]%s[/span]", nestingLevel, dateSpan, statusSpan, authorSpan, textSpan);
    }

    private WorkItemCommentsProcessor.CommentData getCommentData(WorkItemComment workItemComment) {
        String date = DATE_FORMAT.format(workItemComment.getCreated());
        String user = workItemComment.getAuthor();
        String commentText = workItemComment.getText();
        boolean resolved = workItemComment.getResolved();
        return WorkItemCommentsProcessor.CommentData.builder()
                .date(date)
                .author(user)
                .text(commentText)
                .resolved(resolved)
                .build();
    }

    @Data
    @Builder
    private static class CommentData {
        private String date;
        private String author;
        private String text;
        private boolean resolved;
    }
}
