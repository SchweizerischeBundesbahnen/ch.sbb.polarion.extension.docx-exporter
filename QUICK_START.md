# Quick start

### Run Pandoc in Docker

Start Pandoc as a REST service within a Docker container, as described [here](https://github.com/SchweizerischeBundesbahnen/pandoc-service).

## Deploy DOCX Exporter to Polarion

Take file `ch.sbb.polarion.extension.docx-exporter-<version>.jar` from page of [releases](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/releases)
and copy it to `<polarion_home>/polarion/extensions/ch.sbb.polarion.extension.docx-exporter/eclipse/plugins` folder.

## Specify required properties in polarion.properties file

Add following properties to file `polarion.properties`:

```properties
com.siemens.polarion.rest.enabled=true
com.siemens.polarion.rest.swaggerUi.enabled=true
ch.sbb.polarion.extension.docx-exporter.pandoc.service=http://localhost:9082
```

## Restart Polarion

Stop Polarion.
Delete the `<polarion_home>/data/workspace/.config` folder.
Start Polarion.

## Configure DOCX Exporter for Live Documents

On admin pane of appropriate project select menu "Documents & Pages ➙ Document Properties Sidebar", insert following new line inside `sections`-element and save your changes:

```xml
<sections>
  …
  <extension id="docx-exporter" label="DOCX Exporter" />
</sections>
```

## Ready to go

DOCX Exporter is now installed and configured. You can now open a Live Document and on Documents Sidebar you will see DOCX Exporter section. Also open About page of DOCX Exporter on admin pane
and make sure that there are no errors in Extension configuration status table.
