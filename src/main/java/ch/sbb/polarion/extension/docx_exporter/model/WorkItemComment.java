package ch.sbb.polarion.extension.docx_exporter.model;

import lombok.Builder;
import lombok.Data;

import java.util.Date;
import java.util.Map;

@Data
@Builder
public class WorkItemComment {
    private String id;
    private String title;
    private String text;
    private String project;
    private String author;
    private Date created;
    private Boolean resolved;
    private Map<String, WorkItemComment> childComments;
}
