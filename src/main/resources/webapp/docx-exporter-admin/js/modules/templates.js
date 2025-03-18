import ExtensionContext from '../../ui/generic/js/modules/ExtensionContext.js';
import ConfigurationsPane from '../../ui/generic/js/modules/ConfigurationsPane.js';

const ctx = new ExtensionContext({
    extension: 'docx-exporter',
    setting: 'templates',
    scopeFieldId: 'scope'
});

const conf = new ConfigurationsPane({
    ctx: ctx,
    setConfigurationContentCallback: setTemplates,
});

ctx.onClick(
    'docx-image', downloadDocx,
    'delete-file-button', deleteTemplate,
    'save-toolbar-button', saveTemplate,
    'cancel-toolbar-button', ctx.cancelEdit,
    'default-toolbar-button', revertToDefault,
    'revisions-toolbar-button', ctx.toggleRevisions,
);

ctx.onChange(
    'template-file-upload', fileChosen
);

function deleteTemplate() {
    ctx.docx = null;
    invalidatePanels();
}

function fileChosen() {
    const file = ctx.getElementById('template-file-upload').files[0];
    // const label = ctx.getElementById('file-name');
    //label.innerText = file === undefined ? 'No file chosen' : file.name;

    if (file) {
        processDocx(file);
    }
}

function processDocx(file) {
    try {
        const reader = new FileReader();
        reader.onload = async function (event) {
            // processDocxContent(event.target.result);
            await processDocxContent(arrayBufferToBinary(reader.result));
        };
        // reader.readAsArrayBuffer(file);
        reader.readAsArrayBuffer(file);
    } catch (error) {
        console.error("Error reading DOCX:", error);
        alert("Uploaded file must be a valid docx file");
        cleanFileUpload();
    }
}

async function processDocxContent(data) {
    ctx.docx = null;
    if (data) {
        const docxDetails = await loadDocxDetails(data);
        if (docxDetails.success) {
            ctx.getElementById('file-info').innerHTML = `Style Count: ${docxDetails.styleCount}<br>Last Modified Date: ${docxDetails.modifiedDate}`;
            ctx.docx = data;
        } else {
            alert("Uploaded file must be a valid docx file");
        }
    }
    invalidatePanels();
}

async function loadDocxDetails(data) {
    const result = {
        success: false,
        styleCount : 'N/A',
        modifiedDate : 'N/A',
    };

    try {
        const parser = new DOMParser();

        const zip = await JSZip.loadAsync(data);
        const stylesXml = await zip.file("word/styles.xml").async("text");
        if (stylesXml) {
            const stylesDoc = parser.parseFromString(stylesXml, "application/xml");
            const styleNodes = stylesDoc.getElementsByTagName("w:style");
            result.styleCount = styleNodes.length;
        }
        const coreXml = await zip.file("docProps/core.xml")?.async("text");
        if (coreXml) {
            const coreDoc = parser.parseFromString(coreXml, "application/xml");
            const modifiedDateNode = coreDoc.getElementsByTagName("dcterms:modified")[0];
            if (modifiedDateNode && modifiedDateNode.textContent) {
                result.modifiedDate = modifiedDateNode.textContent.replace(/[TZ]/g, ' ');
            }
        }
        result.success = true;
    } catch (error) {
        console.error("Error parsing DOCX content:", error);
    }
    return result;
}

function cleanFileUpload() {
    ctx.getElementById('template-file-upload').value = '';
}

function invalidatePanels() {
    ctx.displayIf('choose-file-panel', !ctx.docx, 'flex');
    ctx.displayIf('file-chosen-panel', ctx.docx, 'flex');
}

function saveTemplate() {
    ctx.hideActionAlerts();
    // const file = ctx.getElementById('template-file-upload').files[0];
    if (ctx.docx) {
        ctx.callAsync({
            method: 'PUT',
            url: `/polarion/${ctx.extension}/rest/internal/settings/${ctx.setting}/names/${conf.getSelectedConfiguration()}/content?scope=${ctx.scope}`,
            contentType: 'application/json',
            body: JSON.stringify({
                'template': btoa(ctx.docx)
            }),
            onOk: () => {
                ctx.showSaveSuccessAlert();
                ctx.setNewerVersionNotificationVisible(false);
                conf.loadConfigurationNames();
            },
            onError: () => ctx.showSaveErrorAlert()
        });
    } else {
        console.log('No file selected.');
    }
}

function downloadDocx() {
    if (ctx.docx) {
        ctx.downloadBlob(new Blob([ctx.docx], {type: "application/vnd.openxmlformats-officedocument.wordprocessingml.document"}), `Template_${conf.getSelectedConfiguration()}.docx`);
    }
}

function setTemplates(text) {
    const templateModel = JSON.parse(text);
    processDocxContent(templateModel.template ? base64ToUint8Array(templateModel.template) : null);
}

function revertToDefault() {
    if (confirm('Are you sure you want to return the default value?')) {
        loadDefaultContent()
            .then((responseText) => {
                setTemplates(responseText);
                ctx.showRevertedToDefaultAlert();
            })
    }
}

function loadDefaultContent() {
    return new Promise((resolve, reject) => {
        ctx.setLoadingErrorNotificationVisible(false);
        ctx.hideActionAlerts();

        ctx.callAsync({
            method: 'GET',
            url: `/polarion/${ctx.extension}/rest/internal/settings/${ctx.setting}/default-content`,
            contentType: 'application/json',
            onOk: (responseText) => resolve(responseText),
            onError: () => {
                ctx.setLoadingErrorNotificationVisible(true);
                reject();
            }
        });
    });
}

function arrayBufferToBinary(arrayBuffer) {
    const bytes = new Uint8Array(arrayBuffer);
    let binaryString = "";
    for (let i = 0; i < bytes.length; i++) {
        binaryString += String.fromCharCode(bytes[i]);
    }
    return binaryString;
}

function base64ToUint8Array(base64) {
    const binaryString = atob(base64);
    const len = binaryString.length;
    const bytes = new Uint8Array(len);
    for (let i = 0; i < len; i++) {
        bytes[i] = binaryString.charCodeAt(i);
    }
    return bytes;
}

conf.loadConfigurationNames();
