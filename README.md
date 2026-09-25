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
The extension uses [Pandoc](https://pandoc.org/) as a converter engine and requires it to run in [Docker as Service](CONFIGURATION.md#pandoc-configuration).

> [!IMPORTANT]
> Only latest version of Polarion is supported.
> Right now it is Polarion 2606.

> [!IMPORTANT]
> Please, read our [disclaimer](DISCLAIMER.md) before using this extension.

## Documentation

| Page | Contents |
| --- | --- |
| [Quick start](QUICK_START.md) | The most important steps and configurations, briefly summarized |
| [Configuration](CONFIGURATION.md) | Full configuration reference: Pandoc service, toolbar injection, webhooks, external resources, logging, workflow function, style packages and more |
| [User guide](USER_GUIDE.md) | The export options in the DOCX Exporter dialog |
| [Limitations and workarounds](LIMITATIONS.md) | By-design behaviour and how to work around it |
| [Upgrade notes](UPGRADE.md) | Version-specific upgrade instructions |
| [REST API](docs/openapi.json) | OpenAPI specification |

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
mvn clean install -P local-install-into-polarion
```
For automated installation with maven env variable `POLARION_HOME` should be defined and point to folder where Polarion is installed.

Changes only take effect after restart of Polarion.

## Usage

1. Open a document in Polarion.
2. In the toolbar choose Show Sidebar ➙ Document Properties.
3. Choose desired options in the `DOCX Exporter` block and click `Export to DOCX`.
   For the options details please refer [user guide](USER_GUIDE.md).

## REST API
This extension provides REST API. OpenAPI Specification can be obtained [here](docs/openapi.json).

For asynchronous export and job-timeout tuning, see [Advanced configuration](CONFIGURATION.md#advanced-configuration).
