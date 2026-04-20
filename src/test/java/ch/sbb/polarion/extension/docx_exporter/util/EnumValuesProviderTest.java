package ch.sbb.polarion.extension.docx_exporter.util;

import ch.sbb.polarion.extension.docx_exporter.rest.model.conversion.LinkRoleDirection;
import com.polarion.alm.tracker.internal.model.LinkRoleOpt;
import com.polarion.alm.tracker.internal.model.TypeOpt;
import com.polarion.alm.tracker.model.ILinkRoleOpt;
import com.polarion.alm.tracker.model.ITrackerProject;
import com.polarion.alm.tracker.model.ITypeOpt;
import com.polarion.platform.persistence.IEnumeration;
import com.polarion.platform.persistence.spi.EnumOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnumValuesProviderTest {

    @Test
    void shouldReturnEmptyListWhenSelectedRoleNamesIsNull() {
        ITrackerProject project = mock(ITrackerProject.class);

        List<String> result = EnumValuesProvider.getLinkRoleNames(project, null, LinkRoleDirection.BOTH);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyListWhenSelectedRoleNamesIsEmpty() {
        ITrackerProject project = mock(ITrackerProject.class);

        List<String> result = EnumValuesProvider.getLinkRoleNames(project, Collections.emptyList(), LinkRoleDirection.BOTH);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnBothNamesWhenDirectionIsBoth() {
        ITrackerProject project = buildProjectWithTwoRoles();

        List<String> result = EnumValuesProvider.getLinkRoleNames(project, List.of("role1", "role2"), LinkRoleDirection.BOTH);

        assertThat(result).containsExactly("role1", "testRole1OppositeName", "role2", "testRole2OppositeName");
    }

    @Test
    void shouldReturnOnlyDirectNamesWhenDirectionIsDirect() {
        ITrackerProject project = buildProjectWithTwoRoles();

        List<String> result = EnumValuesProvider.getLinkRoleNames(project, List.of("role1", "role2"), LinkRoleDirection.DIRECT);

        assertThat(result).containsExactly("role1", "role2");
    }

    @Test
    void shouldReturnOnlyReverseNamesWhenDirectionIsReverse() {
        ITrackerProject project = buildProjectWithTwoRoles();

        List<String> result = EnumValuesProvider.getLinkRoleNames(project, List.of("role1", "role2"), LinkRoleDirection.REVERSE);

        assertThat(result).containsExactly("testRole1OppositeName", "testRole2OppositeName");
    }

    @Test
    void shouldDefaultToBothWhenDirectionIsNull() {
        ITrackerProject project = buildProjectWithTwoRoles();

        List<String> result = EnumValuesProvider.getLinkRoleNames(project, List.of("role1", "role2"), null);

        assertThat(result).containsExactly("role1", "testRole1OppositeName", "role2", "testRole2OppositeName");
    }

    @SuppressWarnings("unchecked")
    private ITrackerProject buildProjectWithTwoRoles() {
        ITrackerProject project = mock(ITrackerProject.class);
        IEnumeration<ILinkRoleOpt> roleEnum = mock(IEnumeration.class);
        Properties props1 = new Properties();
        props1.put("oppositeName", "testRole1OppositeName");
        ILinkRoleOpt option1 = new LinkRoleOpt(new EnumOption("roleLink", "role1", "role1", 1, false, props1));
        Properties props2 = new Properties();
        props2.put("oppositeName", "testRole2OppositeName");
        ILinkRoleOpt option2 = new LinkRoleOpt(new EnumOption("roleLink", "role2", "role2", 1, false, props2));
        when(roleEnum.getAvailableOptions("wiType")).thenReturn(List.of(option1, option2));
        IEnumeration<ITypeOpt> typeEnum = mock(IEnumeration.class);
        TypeOpt typeOption = new TypeOpt(new EnumOption("wiType", "wiType", "WIType", 1, false));
        when(typeEnum.getAllOptions()).thenReturn(List.of(typeOption));
        when(project.getWorkItemTypeEnum()).thenReturn(typeEnum);
        when(project.getWorkItemLinkRoleEnum()).thenReturn(roleEnum);
        return project;
    }
}
