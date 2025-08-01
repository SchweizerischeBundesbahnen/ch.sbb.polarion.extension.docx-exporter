package ch.sbb.polarion.extension.docx_exporter.util.html;

import ch.sbb.polarion.extension.generic.regex.RegexMatcher;
import ch.sbb.polarion.extension.docx_exporter.util.FileResourceProvider;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class HtmlLinksHelper {
    private static final String LINK_REGEX = "<link\\s*[^<>]*>";
    private static final String ATTRIBUTE_REGEX = "([\\w-]+)\\s*=\\s*(['\"])(.*?)\\2";

    private final Set<LinkInternalizer> linkInliners;

    public HtmlLinksHelper(FileResourceProvider fileResourceProvider) {
        this (Set.of(
                new ExternalCssInternalizer(fileResourceProvider)
        ));
    }

    public HtmlLinksHelper(Set<LinkInternalizer> linkInliners) {
        this.linkInliners = linkInliners;
    }

    public static Map<String, String> parseLinkTagAttributes(String linkTag) {
        Map<String, String> attributes = new LinkedHashMap<>();

        RegexMatcher.get(ATTRIBUTE_REGEX).useJavaUtil().processEntry(linkTag, regexEngine -> {
            String attributeName = regexEngine.group(1);
            String attributeValue = regexEngine.group(3);
            attributes.put(attributeName.toLowerCase(), attributeValue);
        });

        return attributes;
    }

    public String internalizeLinks(String htmlContent) {
        return RegexMatcher.get(LINK_REGEX, RegexMatcher.CASE_INSENSITIVE).replace(htmlContent, regexEngine -> {
            String linkTag = regexEngine.group(0);
            Map<String, String> attributesMap = parseLinkTagAttributes(linkTag);
            return inlineLinkTag(linkTag, attributesMap);
        });
    }

    private String inlineLinkTag(String linkTag, Map<String, String> attributesMap) {
        return linkInliners.stream()
                .map(i -> i.inline(attributesMap))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst()
                .orElse(linkTag);
    }
}
