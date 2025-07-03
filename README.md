[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.docx-exporter&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.docx-exporter)
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.docx-exporter&metric=bugs)](https://sonarcloud.io/summary/new_code?id=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.docx-exporter)
[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.docx-exporter&metric=code_smells)](https://sonarcloud.io/summary/new_code?id=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.docx-exporter)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.docx-exporter&metric=coverage)](https://sonarcloud.io/summary/new_code?id=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.docx-exporter)
[![Duplicated Lines (%)](https://sonarcloud.io/api/project_badges/measure?project=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.docx-exporter&metric=duplicated_lines_density)](https://sonarcloud.io/summary/new_code?id=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.docx-exporter)
[![Lines of Code](https://sonarcloud.io/api/project_badges/measure?project=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.docx-exporter&metric=ncloc)](https://sonarcloud.io/summary/new_code?id=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.docx-exporter)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.docx-exporter&metric=reliability_rating)](https://sonarcloud.io/summary/new_code?id=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.docx-exporter)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.docx-exporter&metric=security_rating)](https://sonarcloud.io/summary/new_code?id=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.docx-exporter)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.docx-exporter&metric=sqale_rating)](https://sonarcloud.io/summary/new_code?id=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.docx-exporter)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.docx-exporter&metric=vulnerabilities)](https://sonarcloud.io/summary/new_code?id=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.docx-exporter)

# Polarion ALM extension to convert Documents to DOCX files

This Polarion extension provides possibility to convert Polarion Documents to DOCX files.
The extension uses [Pandoc](https://pandoc.org/) as a converter engine and requires it to run in [Docker as Service](#pandoc-configuration).

> [!IMPORTANT]
> Only latest version of Polarion is supported.
> Right now it is Polarion 2506.

## Quick start

Please see separate [quick start page](QUICK_START.md) where briefly summarized all most important and applicable steps and configurations.

If you need deeper knowledge about all possible steps, configurations and their descriptions, please see sections below.

## Build

This extension can be produced using maven:
```bash
mvn clean package
```

## Installation to Polarion

To install the extension to Polarion `ch.sbb.polarion.extension.docx-exporter-<version>.jar`
should be copied to `<polarion_home>/polarion/extensions/ch.sbb.polarion.extension.docx-exporter/eclipse/plugins`
It can be done manually or automated using maven build:
```bash
mvn clean install -P install-to-local-polarion
```
For automated installation with maven env variable `POLARION_HOME` should be defined and point to folder where Polarion is installed.

Changes only take effect after restart of Polarion.

## Polarion configuration

### Pandoc configuration

This extension supports the use of Pandoc as a REST service within a Docker container, as implemented [here](https://github.com/SchweizerischeBundesbahnen/pandoc-service).
To change Pandoc Service URL, adjust the following property in the `polarion.properties` file:

```properties
ch.sbb.polarion.extension.docx-exporter.pandoc.service=http://localhost:9082
```

### DOCX exporter extension to appear on a Document's properties pane

1. Open a project where you wish DOCX Exporter to be available
2. On the top of the project's navigation pane click ⚙ (Actions) ➙ 🔧 Administration. Project's administration page will be opened.
3. On the administration's navigation pane select Documents & Pages ➙ Document Properties Sidebar.
4. In opened Edit Project Configuration editor find `sections`-element:
   ```xml
   …
   <sections>
     <section id="fields"/>
     …
   </sections>
   …
   ```
5. And insert following new line inside this element:
   ```xml
   …
   <extension id="docx-exporter" label="DOCX Exporter" />
   …
   ```
6. Save changes by clicking 💾 Save

### DOCX Exporter view to open via button in toolbar

Alternatively you can configure DOCX Exporter such a way that additional toolbar will appear in document's editor with a button to open a popup with DOCX Exporter view.

1. Open "Default Repository".
2. On the top of its navigation pane click ⚙ (Actions) ➙ 🔧 Administration. Global administration page will be opened.
3. On the administration's navigation pane select Configuration Properties.
4. In editor of opened page add following line:
   ```properties
   scriptInjection.dleEditorHead=<script src="/polarion/docx-exporter/js/starter.js"></script><script>DocxExporterStarter.injectToolbar();</script>
   ```
   There's an alternate approach adding DOCX Exporter button into native Polarion's toolbar, which has a drawback at the moment -
   button disappears in some cases (for example when document is saved), so using this approach is not advisable:
   ```properties
   scriptInjection.dleEditorHead=<script src="/polarion/docx-exporter/js/starter.js"></script><script>DocxExporterStarter.injectToolbar({alternate: true});</script>
   ```
5. Save changes by clicking 💾 Save

### Configuring logs

For better problem analyses extended logging can be configured in Polarion. By default, Polarion log level is set to INFO. It can be changed to debug in `log4j2.xml` file.
Find `/opt/polarion/polarion/plugins/com.polarion.core.util_<version>/log4j2.xml` file and add the following line into `Loggers`section:
```xml
<Logger name="ch.sbb.polarion.extension" level="debug"/>
```

It is also possible to write all messages of SBB extensions info separate log file which can be useful to report a problem. In this case new `Appender` should be added:
```xml
<RollingFile name="SBB" fileName="${sys:logDir}/log4j-sbb${fileNameSuffix}" filePattern="${sys:logDir}/log4j-sbb${filePatternSuffix}">
    <PatternLayout pattern="${layoutPattern}"/>
    <Policies>
        <TimeBasedTriggeringPolicy interval="1"/>
    </Policies>
</RollingFile>
```
and the following `Logger`:
```xml
<Logger name="ch.sbb.polarion.extension" level="debug">
    <AppenderRef ref="SBB"/>
</Logger>
```

### Enabling CORS

Cross-Origin Resource Sharing could be enabled using standard configuration of Polarion REST API. In `polarion.properties` the following lines should be added:
```properties
com.siemens.polarion.rest.enabled=true
com.siemens.polarion.rest.cors.allowedOrigins=http://localhost:8888,https://anotherdomain.com
```

### Enabling webhooks

By default, webhooks functionality is not enabled in DOCX Exporter. If you want to make it available the following line should be added in `polarion.properties`:
```properties
ch.sbb.polarion.extension.docx-exporter.webhooks.enabled=true
```

### Debug option

This extension makes intensive HTML processing to extend similar standard Polarion functionality. There is a possibility to log
original and resulting HTML to see potential problems in this processing. This logging can be switched on (`true` value)
and off (`false` value) with help of following property in file `polarion.properties`:

```properties
ch.sbb.polarion.extension.docx-exporter.debug=true
```

If HTML logging is switched on, then in standard polarion log file there will be following lines:

```text
2023-09-20 08:42:13,911 [ForkJoinPool.commonPool-worker-2] INFO  util.ch.sbb.polarion.extension.docx_exporter.util.HtmlLogger - Original HTML fragment provided by Polarion was stored in file /tmp/docx-exporter10000032892830031969/original-4734772539141140796.html
2023-09-20 08:42:13,914 [ForkJoinPool.commonPool-worker-2] INFO  util.ch.sbb.polarion.extension.docx_exporter.util.HtmlLogger - Final HTML page obtained as a result of DOCX exporter processing was stored in file /tmp/docx-exporter10000032892830031969/processed-5773281490308773124.html
```

Here you can find out in which files HTML was stored.

### Workflow function configuration
It is possible to configure the workflow function which exports a DOCX file and attaches it to a newly created or already existing work item.

To create workflow functions do following:
1. On the top of the project's navigation pane click ⚙ (Actions) ➙ 🔧 Administration. Project's administration pane will be opened.
2. On the administration's navigation pane select Documents & Pages ➙ Document Workflow.
3. On the opened page you will see a list of document types with their actions. Find type you are interested in and click `Edit` or `Create` button for it.
4. On the opened page (Workflow Designer) find the section Actions, appropriate action in it, e.g. `archive` (or create a new one) and click `Edit` for it.
5. A popup will be opened with title 'Details for Action: Archive', select 'DOCX Export' in 'Function' dropdown of 'Functions' section and then click
   pencil button. Another popup will be opened with title 'Parameter for: DOCX Export', add appropriate parameters in table of this popup, then click `Close`.
   Then again `Close` on previous popup and finally `Save` when you will be back on Workflow Designer page.

Supported function parameters:

| Parameter             | Required | Description                                                                 | Default value                                                                                                                     |
|-----------------------|----------|-----------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------|
| existing_wi_id        | yes (*)  | Workitem ID to reuse                                                        | -                                                                                                                                 |
| create_wi_type_id     | yes (*)  | Type ID of workitem to create                                               | -                                                                                                                                 |
| create_wi_title       | no       | Value to set as a workitem title (used only with 'create_wi_type_id')       | Value like "modified document title with space -> target status name" (e.g., "Specification / Product Specification -> Archived") |
| create_wi_description | no       | Value to set as a workitem description (used only with 'create_wi_type_id') | "This item was created automatically. Check 'Attachments' section for the generated DOCX document."                               |
| project_id            | no       | Project ID where to create or search for the target work item               | Project ID of the modified document                                                                                               |
| attachment_title      | no       | The title of the attached file                                              | The name of the generated file (without '.docx' at the end)                                                                       |
| style_package         | no       | The name of the style package to use                                        | Default                                                                                                                           |
| prefer_last_baseline  | no       | Use the last baseline revision instead of the last document's revision      | false                                                                                                                             |

(*) - either 'existing_wi_id' or 'create_wi_type_id' parameter required.
Providing the first one means reuse already existing workitem to attach the file whereas the second will create a new workitem with the specified type.
In case if both of them specified 'existing_wi_id' has higher priority.

## Extension configuration

1. On the top of the project's navigation pane click ⚙ (Actions) ➙ 🔧 Administration. Project's administration page will be opened.
2. On the administration's navigation pane select `DOCX Export`. There are expandable sub-menus with different configuration options for DOCX Exporter.
3. For some of these options (Localization, Webhooks and Filename template) `Quick Help` section available with option short description. For the rest
   (Style Package, Style Package Weights) there's no `Quick Help` section as their content is self-evident.
4. To change configuration of DOCX Exporter extension just edit corresponding section and press `Save` button.

## Usage

1. Open a document in Polarion.
2. In the toolbar choose Show Sidebar ➙ Document Properties.
3. Choose desired options in the `DOCX Exporter` block and click `Export to DOCX`.
   For the options details please refer [user guide](USER_GUIDE.md).

## REST API
This extension provides REST API. OpenAPI Specification can be obtained [here](docs/openapi.json).

## Advanced configuration

### Asynchronous DOCX Export: export jobs timeout
This extension provides REST API to export DOCX asynchronously. Using this API, it is possible to start export job, observe their status and get result.
Finished (succeed or failed) and in-progress export jobs will be preserved in memory until configured timeout. To change this timeout, adjust the following property in the local `docx-converter-jobs.properties` file:
```properties
# Timeout in minutes to keep finished async conversion jobs results in memory
jobs.timeout.finished.minutes=30
# Timeout in minutes to wait until async conversion jobs is finished
jobs.timeout.in-progress.minutes=60
```

## Known issues

All good so far.
