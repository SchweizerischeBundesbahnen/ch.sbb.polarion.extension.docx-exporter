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
@SuppressWarnings({"rawtypes", "unchecked"})
class WorkItemCommentsProcessorTest {
    @Test
    void addWorkItemComments_withOneWorkItemAndOneCommentTest() {
        // Arrange
        ProxyDocument mockDocument = mock(ProxyDocument.class);
        IWorkItem mockWorkItem = mock(IWorkItem.class);
        when(mockWorkItem.getId()).thenReturn("EL-219");
        when(mockWorkItem.isUnresolvable()).thenReturn(false);
        IWorkItem unresolvableMockWorkItem = mock(IWorkItem.class);
        when(unresolvableMockWorkItem.isUnresolvable()).thenReturn(true);

        Text mockText = mock(Text.class);
        IProject mockProject = mock(IProject.class);
        when(mockProject.getId()).thenReturn("Project1");

        IModule mockOldApi = mock(IModule.class);

        when(mockDocument.getOldApi()).thenReturn(mockOldApi);
        when(mockOldApi.getAllWorkItems()).thenReturn(List.of(mockWorkItem, unresolvableMockWorkItem));

        IUser mockUser1 = mock(IUser.class);
        IComment mockComment1 = mock(IComment.class);
        when(mockComment1.getId()).thenReturn("c1");
        when(mockComment1.getTitle()).thenReturn("Title 1");
        when(mockComment1.getText()).thenReturn(mockText);
        when(mockText.getContent()).thenReturn("This is a comment");
        when(mockComment1.getProject()).thenReturn(mockProject);
        when(mockComment1.getAuthor()).thenReturn(mockUser1);
        when(mockUser1.getName()).thenReturn("System Administrator");
        when(mockComment1.getCreated()).thenReturn(new Date(0));
        when(mockComment1.isResolved()).thenReturn(false);
        when(mockComment1.getChildComments()).thenReturn(IPObjectList.EMPTY_POBJECTLIST);

        IUser mockUser2 = mock(IUser.class);
        IComment mockComment2 = mock(IComment.class);
        when(mockComment2.getId()).thenReturn("c2");
        when(mockComment2.getTitle()).thenReturn("Title 2 visible");
        when(mockComment2.getText()).thenReturn(null); // this time - no text
        when(mockComment2.getProject()).thenReturn(mockProject);
        when(mockComment2.getAuthor()).thenReturn(mockUser2);
        when(mockUser2.getName()).thenReturn("Basic User");
        when(mockComment2.getCreated()).thenReturn(new Date(0));
        when(mockComment2.isResolved()).thenReturn(false);
        when(mockComment2.getChildComments()).thenReturn(IPObjectList.EMPTY_POBJECTLIST);

        IPObjectList mockObjectList = mock(IPObjectList.class);
        when(mockObjectList.iterator()).thenReturn(List.of(mockComment1, mockComment2).iterator());
        when(mockWorkItem.getRootComments(false)).thenReturn(mockObjectList);

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
        assertThat(updatedHtml).doesNotContain("Title 1");
        assertThat(updatedHtml).contains("Title 2 visible");
    }
}
