<%@ page import="ch.sbb.polarion.extension.docx_exporter.properties.DocxExporterExtensionConfiguration" %>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">

<%! String bundleTimestamp = ch.sbb.polarion.extension.generic.util.VersionUtils.getVersion().getBundleBuildTimestampDigitsOnly(); %>
<%! Boolean webhooksEnabled = DocxExporterExtensionConfiguration.getInstance().getWebhooksEnabled(); %>

<head>
    <title>DOCX Exporter: Style Packages</title>
    <link rel="stylesheet" href="../ui/generic/css/prism.css?bundle=<%= bundleTimestamp %>">
    <script type="text/javascript" src="../ui/generic/js/prism.js?bundle=<%= bundleTimestamp %>"></script>
    <link rel="stylesheet" href="../ui/generic/css/code-input.min.css?bundle=<%= bundleTimestamp %>">
    <script type="text/javascript" src="../ui/generic/js/code-input.min.js?bundle=<%= bundleTimestamp %>"></script>
    <link rel="stylesheet" href="../ui/generic/css/common.css?bundle=<%= bundleTimestamp %>">
    <link rel="stylesheet" href="../ui/generic/css/custom-select.css?bundle=<%= bundleTimestamp %>">
    <link rel="stylesheet" href="../ui/generic/css/configurations.css?bundle=<%= bundleTimestamp %>">
    <script type="module" src="../js/modules/style-packages.js?bundle=<%= bundleTimestamp %>"></script>
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

        .style-package-error {
            color: red;
        }

        .flex-container {
            display: flex;
            column-gap: 20px;
            flex-wrap: wrap;
        }

        .flex-column {
            width: 440px;
        }

        .input-group {
            margin-bottom: 10px;
        }

        input[type="checkbox"] {
            width: auto;
            vertical-align: middle;
        }

        .checkbox.input-group label {
            width: auto;
        }

        .flex-centered {
            display: flex;
            align-items: center;
        }

        .flex-centered label {
            width: auto;
            margin-right: 4px;
        }

        .flex-grow {
            flex-grow: 1;
        }

        .more-info {
            background: url(/polarion/ria/images/msginfo.png) no-repeat;
            display: inline-block;
            width: 17px;
            height: 17px;
            cursor: pointer;
        }
    </style>
</head>

<body>
<div class="standard-admin-page">
    <h1>DOCX Exporter: Style Packages</h1>

    <jsp:include page='/common/jsp/notifications.jsp'/>

    <jsp:include page='/common/jsp/configurations.jsp'/>

    <div class="content-area">
        <div id="child-configs-load-error" class="style-package-error" style="display: none; margin-bottom: 15px">
            There was an error loading names of children configurations. Please, contact project/system administrator to solve the issue, a style package can't be configured without them.
        </div>
        <div id="link-roles-load-error" class="style-package-error" style="display: none; margin-bottom: 15px">
            There was an error loading link role names.
        </div>

        <div class="flex-container" style="border-top: 1px solid #ccc; margin-top: 20px; padding-top: 15px;">

            <div class="flex-column">
                <div class='input-group flex-centered'>
                    <label for='style-package-weight'>Weight:</label>
                    <div class='more-info'
                         title="A float number from 0.0 to 100, which will determine the position of current style package in the resulting style packages list. The higher the number, the higher its position will be."></div>
                    <input id="style-package-weight" style="margin-left: 59px" type="number" min="1" max="100" step="0.1">
                </div>
            </div>

            <div class="flex-grow" id="matching-query-container">
                <div class='input-group flex-centered'>
                    <label for='matching-query'>Matching query:</label>
                    <div class='more-info'
                         title="A query to select documents to which this style package will be relevant. For documents not matching this query the style package won't be visible. If you want to make this style package be available to all documents, just leave this field empty."></div>
                    <input id='matching-query' class="flex-grow" style="margin-left: 8px;"/>
                </div>
            </div>

        </div>

        <div class="flex-container" style="border-top: 1px solid #ccc; margin-top: 20px; padding-top: 15px;">
            <div class="flex-column">
                <div class='checkbox input-group'>
                    <label for='exposeSettings'>
                        <input id='exposeSettings' type='checkbox'/>
                        Expose style package settings to be redefined on UI
                    </label>
                </div>
            </div>
        </div>

        <div class="flex-container">
            <div class="flex-column">
                <div class="input-group">
                    <label for="template-select" id="template-select-label">Template:</label>
                    <div id="template-select"></div>
                </div>
            </div>
        </div>

        <div class="flex-container">
            <div class="flex-column">
                <div class="input-group">
                    <label for="localization-select" id="localization-select-label">Localization:</label>
                    <div id="localization-select"></div>
                </div>
            </div>
        </div>

        <div class="flex-container">
            <div class="flex-column">
                <div class='checkbox input-group'>
                    <label for='orientation' id='orientation-label'>
                        <input id="orientation" onchange='document.getElementById("orientation-select").style.visibility = this.checked ? "visible" : "hidden"' type='checkbox'/>
                        Custom orientation
                    </label>
                    <div id="orientation-select" style="visibility: hidden; margin-left: 10px; width: 200px"></div>
                </div>
            </div>
        </div>

        <div class="flex-container">
            <div class="flex-column">
                <div class='checkbox input-group'>
                    <label for='paper-size' id='paper-size-label'>
                        <input id="paper-size" onchange='document.getElementById("paper-size-select").style.visibility = this.checked ? "visible" : "hidden"' type='checkbox'/>
                        Custom paper size
                    </label>
                    <div id="paper-size-select" style="visibility: hidden; margin-left: 10px; width: 200px"></div>
                </div>
            </div>
        </div>

        <div class="flex-container" style="border-top: 1px solid #ccc; margin-top: 20px; padding-top: 15px; display: <%= webhooksEnabled ? "flex" : "none" %>;">
            <div class="flex-column">
                <div class="input-group">
                    <label for='webhooks-checkbox' style="width: 120px;">
                        <input id="webhooks-checkbox" onchange='document.getElementById("webhooks-select").style.display = this.checked ? "inline-block" : "none"' type='checkbox'/>
                        Use webhooks
                    </label>
                    <div id="webhooks-select"></div>
                </div>
            </div>
        </div>

        <div class="flex-container">
            <div class="flex-column">
                <div class='checkbox input-group'>
                    <label for='render-comments' id='render-comments-label'>
                        <input id="render-comments" onchange='document.getElementById("render-comments-select").style.visibility = this.checked ? "visible" : "hidden"' type='checkbox'/>
                        Comments rendering
                    </label>
                    <div id="render-comments-select" style="visibility: hidden; margin-left: 10px; width: 200px"></div>
                </div>
                <div class='checkbox input-group'>
                    <label for='cut-empty-wi-attributes'>
                        <input id="cut-empty-wi-attributes" type='checkbox'/>
                        Cut empty Workitem attributes
                    </label>
                </div>
            </div>
            <div class="flex-column">
                <div class='checkbox input-group'>
                    <label for='cut-empty-chapters'>
                        <input id='cut-empty-chapters' type='checkbox'/>
                        Cut empty chapters (any level)
                    </label>
                </div>
                <div class='checkbox input-group'>
                    <label for='cut-urls'>
                        <input id='cut-urls' type='checkbox'/>
                        Cut local Polarion URLs
                    </label>
                </div>
            </div>
        </div>
        <div class="flex-container">
            <div class="flex-column">
                <div class='checkbox input-group'>
                    <label for='specific-chapters'>
                        <input id='specific-chapters' onchange='document.getElementById("chapters").style.visibility = this.checked ? "visible" : "hidden"' type='checkbox'/>
                        Specific higher level chapters
                    </label>
                    <input id='chapters' placeholder='eg. 1,2,4 etc.' type='text' style="visibility: hidden; margin-left: 10px; width: 117px"/>
                </div>
                <div class='checkbox input-group'>
                    <label for='selected-roles' style="margin-top: 5px">
                        <input id="selected-roles" onchange='document.getElementById("roles-select").style.display = this.checked ? "inline-block" : "none"' type='checkbox'/>
                        Specific Workitem roles
                    </label>
                    <div id="roles-select" style="display: none; margin-left: 10px; width: 152px"></div>
                </div>
            </div>
            <div class="flex-column">
                <div class='checkbox input-group'>
                    <label for='localization'>
                        <input id="localization" onchange='document.getElementById("language-select").style.visibility = this.checked ? "visible" : "hidden"' type='checkbox'/>
                        Localize enums
                    </label>
                    <div id="language-select" style="visibility: hidden; margin-left: 10px; width: 200px"></div>
                </div>
                <div class='checkbox input-group'>
                    <label for='toc'>
                        <input id="toc" type='checkbox'/>
                        ToC
                    </label>
                </div>
            </div>
        </div>

        <div class="flex-container">
            <div class="input-group flex-centered">
                <label for="removal-selector-input" id="removal-selector-label" style="width: auto">Removal selector:</label>
                <div class='more-info'
                     title="CSS-like selector(s) for elements to remove. Example: img.specificClass removes images with that class. Separate multiple selectors with commas, e.g. img.specificClass, table.unwanted, div#ad-banner."></div>
                <input id="removal-selector-input" style="margin-left: 35px; width: 615px" type="text">
            </div>
        </div>

    </div>

    <input id="scope" type="hidden" value="<%= request.getParameter("scope")%>"/>
    <input id="bundle-timestamp" type="hidden" value="<%= ch.sbb.polarion.extension.generic.util.VersionUtils.getVersion().getBundleBuildTimestamp() %>"/>
</div>

<jsp:include page='/common/jsp/buttons.jsp'/>

</body>
</html>
