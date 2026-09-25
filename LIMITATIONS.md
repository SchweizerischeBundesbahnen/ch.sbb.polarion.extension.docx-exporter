# Limitations and Workarounds

This page collects content-specific behaviour that is by design, together with a workaround you can
apply yourself. See the [README](README.md) for a quick overview and the
[configuration reference](CONFIGURATION.md) for all settings.

## Outline (chapter) numbers are displayed twice

The outline (chapter) numbers are currently showing up twice because Word is adding its own numbering. To fix this, heading numbering should be disabled in the Word template used by Pandoc.

## Style of table is not assigned automatically

Styles were not applied because the table style in the Word template was defined incorrectly. When converting HTML to DOCX, Pandoc applies only the style named Table, which is considered the default table style. Even if the template contains other custom table styles, Pandoc ignores them unless they are directly linked to the Table style.
To use a custom table style, the Word template must be configured so that the Table style inherits the formatting of the custom style. This can be done by redefining the default Table style within the template or by setting it to be based on the desired custom style.

## Heading levels beyond h6

HTML supports heading elements only from h1 to h6, but Polarion documents can contain deeper nesting levels. Added with [pandoc-service#152](https://github.com/SchweizerischeBundesbahnen/pandoc-service/pull/152), a Lua filter is included that handles heading levels beyond h6. It converts `<div class="heading-N">` elements (where N > 6) into proper Pandoc header blocks, enabling correct processing of deeply nested document structures. Heading levels are capped at 9 for compatibility with Word documents, and the Table of Contents depth has been extended to support levels 1–9.
