<%@ page import="ch.sbb.polarion.extension.docx_exporter.properties.DocxExporterExtensionConfiguration" %>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">

<%! String bundleTimestamp = ch.sbb.polarion.extension.generic.util.VersionUtils.getVersion().getBundleBuildTimestampDigitsOnly(); %>

<head>
    <title>DOCX Exporter: Templates</title>
    <link rel="stylesheet" href="../ui/generic/css/common.css?bundle=<%= bundleTimestamp %>">
    <link rel="stylesheet" href="../ui/generic/css/custom-select.css?bundle=<%= bundleTimestamp %>">
    <link rel="stylesheet" href="../ui/generic/css/configurations.css?bundle=<%= bundleTimestamp %>">
    <script type="application/javascript" src="../js/jszip.min.js?bundle=<%= bundleTimestamp %>"></script>
    <script type="module" src="../js/modules/templates.js?bundle=<%= bundleTimestamp %>"></script>
    <style type="text/css">
        html {
            height: 100%;
        }

        body {
            height: 100%;
            padding-left: 10px;
            padding-right: 10px;
            margin: 0;
            display: flex;
            flex-direction: column;
        }

        .standard-admin-page {
            flex: 1;
            display: flex;
            flex-direction: column;
        }

        input[type="file"] {
            display: none;
        }

        .upload-hint {
            text-align: left;
            width: auto;
            margin: 4px 0px;
            padding: 16px 4px 16px 36px;
            background: #D8E4F1 url(/polarion/ria/images/msginfo.png) 10px 18px no-repeat;
        }

        .docx-panel {
            width: 300px;
            border: 1px solid #ccc;
            display: flex;
            flex-direction: column;
            align-items: center;
            padding: 20px;
            margin-top: 20px;
        }

        .docx-panel > * {
            margin-top: 15px;
        }

        #template-file-upload-label {
            align-content: center;
        }

        #docx-image {
            cursor: pointer;
        }
    </style>
</head>

<body>
<div style="display: flex; flex: 1; flex-direction: column">
    <div class="standard-admin-page">
        <h1>DOCX Exporter: Templates</h1>

        <jsp:include page='/common/jsp/notifications.jsp'/>

        <jsp:include page='/common/jsp/configurations.jsp'/>

        <h2 class="align-left">DOCX Template</h2>

        <span class="upload-hint">
            For best results, the reference docx should be a modified version of a docx file built-in into Pandoc (<a href="/polarion/docx-exporter/rest/internal/template">download</a>).</span>

        <div id="choose-file-panel" class="docx-panel">
            <img src="/polarion/docx-exporter-admin/ui/images/docx_gray.png" alt="docx"/>
            <span id="no-file-provided">No file provided</span>
            <label id="template-file-upload-label" for="template-file-upload" class="toolbar-button styled-upload-button">Choose docx template file</label>
            <input id="template-file-upload" name="file" type="file" accept=".docx"/>
        </div>

        <div id="file-chosen-panel" class="docx-panel">
            <img id="docx-image" src="/polarion/docx-exporter-admin/ui/images/docx.png" alt="docx"/>
            <span id="file-info"></span>
            <button id="delete-file-button" class="toolbar-button" type="button">
                <img class="button-image" alt="Cancel" title="Delete template" src="/polarion/ria/images/actions/cancel.gif?bundle=<%= bundleTimestamp %>">Delete attached file
            </button>
        </div>

        <input id="scope" type="hidden" value="<%= request.getParameter("scope")%>"/>
        <input id="bundle-timestamp" type="hidden" value="<%= ch.sbb.polarion.extension.generic.util.VersionUtils.getVersion().getBundleBuildTimestamp() %>"/>
    </div>

    <jsp:include page='/common/jsp/buttons.jsp'/>

    <div class="standard-admin-page help">
        <h2 class="align-left">Quick Help</h2>

        <div>
            <p>On this page you can add or remove a template docx file applied to selected configuration.</p>

            <h3>What is a template</h3>
            <p>
                A template (also "reference doc" in terms of Pandoc) is a file which is used as a style reference in producing a docx.
            </p>
            <h3>How it works</h3>
            <p>
                The contents of the template docx are ignored, but its stylesheets and document properties (including margins, page size, header, and footer) are used in the new docx.
                If no reference docx is provided then pandoc-service will use built-in template (which also can be downloaded from this page using 'download' link above).
                Please refer official <a href="https://pandoc.org/MANUAL.html#option--reference-doc" target="_blank">pandoc manual</a> for more information.
            </p>
        </div>
    </div>
</div>
</body>
</html>
