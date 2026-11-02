# Upgrade

Version-specific upgrade notes for the DOCX Exporter extension. See the [README](README.md) for
installation and the [configuration reference](CONFIGURATION.md) for all settings.

## Supported Polarion version

Each major version of the extension targets one Polarion release and only that one — a new major is
released whenever Polarion support moves forward, and it is a breaking change because the previous
Polarion version is no longer supported. Install the extension major that matches your Polarion:

| Extension major | Requires Polarion |
| --- | --- |
| 5.x | 2606 |
| 4.x | 2512 |
| 2.x – 3.x | 2506 |

Upgrading across a major therefore means upgrading Polarion to the matching release first, then
installing the extension jar as described in the [Quick Start](QUICK_START.md#deploy-docx-exporter-to-polarion). Changes
only take effect after a restart of Polarion.

## Pandoc service

The extension talks to [pandoc-service](https://github.com/SchweizerischeBundesbahnen/pandoc-service)
over REST, and a given extension version expects a compatible service. When you upgrade the extension,
upgrade the pandoc-service container as well, and confirm the connection on the **About** page — it
reports the service version and whether the configured API key and transport are accepted before anyone
exports. See [Pandoc configuration](CONFIGURATION.md#pandoc-configuration) for the service URL and
[Pandoc API key](CONFIGURATION.md#pandoc-api-key) for the credential.

Use pandoc-service 2.9.1 or newer. Versions before 2.8.0 write no fixed column widths into a table, so Word
sizes each table by its content: the Work Item attributes tables of one document then get different label
and value columns instead of the same 20% / 80% split. 2.8.0 splits a table into equal columns as soon as a
cell holds a line break, and needs gigabytes of memory for a document with thousands of embedded images.
2.9.0 narrows an image in a table cell below its width, turns a nested table into text with preserved table
styles, and fails to render a nested table to PDF.

## Upgrade from version 5.x.x to 5.6.0

The **User Guide** administration entry is replaced by a single **Documentation** entry. It opens a
documentation site - Quick Start, User Guide, Configuration, Limitations and Upgrade - with its own
navigation, search and cross-links between the articles, so none of these articles has a menu entry of
its own.

The admin node id changed with it, from `user-guide` to `documentation`. A bookmark or a link that points
at the old node, e.g. `#/administration/docx-export/user-guide` or
`#/project/<id>/administration/docx-export/user-guide`, no longer opens a page: replace `user-guide` with
`documentation` in it. The User Guide itself is one click away in the documentation sidebar.
