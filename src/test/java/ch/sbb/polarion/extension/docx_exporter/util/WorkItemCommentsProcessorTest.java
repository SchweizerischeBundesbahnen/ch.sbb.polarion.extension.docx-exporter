package ch.sbb.polarion.extension.docx_exporter.util;

import com.polarion.alm.projects.model.IProject;
import com.polarion.alm.projects.model.IUser;
import com.polarion.alm.server.api.model.document.ProxyDocument;
import com.polarion.alm.tracker.model.IComment;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.core.util.types.Text;
import com.polarion.platform.persistence.model.IPObjectList;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class WorkItemCommentsProcessorTest {
    @Test
    void addWorkItemComments_withOneWorkItemAndOneCommentTest() {
        // Arrange
        ProxyDocument mockDocument = mock(ProxyDocument.class);
        IWorkItem mockWorkItem = mock(IWorkItem.class);
        IComment mockComment = mock(IComment.class);
        Text mockText = mock(Text.class);
        IProject mockProject = mock(IProject.class);
        IUser mockUser = mock(IUser.class);
        IModule mockOldApi = mock(IModule.class);

        when(mockDocument.getOldApi()).thenReturn(mockOldApi);
        when(mockOldApi.getAllWorkItems()).thenReturn(List.of(mockWorkItem));
        when(mockWorkItem.getId()).thenReturn("EL-219");
        IPObjectList mockObjectList = mock(IPObjectList.class);
        when(mockObjectList.iterator()).thenReturn(List.of(mockComment).iterator());
        when(mockWorkItem.getRootComments(false)).thenReturn(mockObjectList);

        when(mockComment.getId()).thenReturn("c1");
        when(mockComment.getTitle()).thenReturn("Title 1");
        when(mockComment.getText()).thenReturn(mockText);
        when(mockText.getContent()).thenReturn("This is a comment");
        when(mockComment.getProject()).thenReturn(mockProject);
        when(mockProject.getId()).thenReturn("Project1");
        when(mockComment.getAuthor()).thenReturn(mockUser);
        when(mockUser.getName()).thenReturn("System Administrator");
        when(mockComment.getCreated()).thenReturn(new Date(0));
        when(mockComment.isResolved()).thenReturn(false);
        when(mockComment.getChildComments()).thenReturn(IPObjectList.EMPTY_POBJECTLIST);

        String inputHtml = "<div id=\"params=id=EL-219\">original content</div>";

        // Act
        String resultHtml = new WorkItemCommentsProcessor().addWorkItemComments(mockDocument, inputHtml, false);

        // Assert
        Document resultDoc = Jsoup.parse(resultHtml);
        String updatedHtml = resultDoc.body().html();

        assertThat(updatedHtml).contains("This is a comment");
        assertThat(updatedHtml).contains("System Administrator");
        assertThat(updatedHtml).contains("level-0");
        assertThat(updatedHtml).contains("original content");
    }
}
