import { expect } from 'chai';
import TestUtils from "./TestUtils.js";
import * as td from 'testdouble';
import { JSDOM } from 'jsdom';

/** @type {typeof ExportContext} */
const ExportContext = await TestUtils.importUsingGeneric('ExportContext.js');

/** @type {typeof ExportPanel} */
const ExportPanel = await TestUtils.importUsingGeneric('ExportPanel.js');

describe('ExportPanel', function () {
    let panel, ctxStub;

    beforeEach(function () {
        const dom = new JSDOM('<!DOCTYPE html><html><body></body></html>', { url: 'http://localhost' });
        global.window = dom.window;
        global.document = dom.window.document;

        ctxStub = td.object(new ExportContext({ polarionLocationHash: window.location.hash }));
        panel = new ExportPanel(ctxStub);
    });

    afterEach(function () {
        td.reset();
    });

    it.skip('should initialize event listeners', function () {
        td.verify(ctxStub.onChange('docx-style-package-select', td.matchers.isA(Function)));
        td.verify(ctxStub.onClick('export-docx', td.matchers.isA(Function)));
    });

    it.skip('should prepare request JSON correctly', function () {
        td.when(ctxStub.getElementById('docx-specific-chapters')).thenReturn({ checked: false });
        td.when(ctxStub.getElementById('docx-selected-roles')).thenReturn({ checked: false });
        td.when(ctxStub.getElementById('docx-localization-selector')).thenReturn({ value: 'en-US' });
        td.when(ctxStub.getElementById('docx-enable-comments-rendering')).thenReturn({ checked: true });
        td.when(ctxStub.getElementById('docx-cut-empty-chapters')).thenReturn({ checked: false });
        td.when(ctxStub.getElementById('docx-cut-empty-wi-attributes')).thenReturn({ checked: true });
        td.when(ctxStub.getElementById('docx-cut-urls')).thenReturn({ checked: false });
        td.when(ctxStub.getElementById('docx-filename')).thenReturn({ value: 'test.docx' });

        const requestJson = panel.prepareRequest('12345', '/path', 10, 20, 'test.docx', 'docx');

        expect(JSON.parse(requestJson)).to.deep.include({
            projectId: '12345',
            locationPath: '/path',
            baselineRevision: 10,
            revision: 20,
            localization: 'en-US',
            enableCommentsRendering: true,
            cutEmptyChapters: false,
            cutEmptyWIAttributes: true,
            cutLocalUrls: false,
            fileName: 'test.docx'
        });
    });

    it.skip('should disable buttons and call async request on style package change', function () {
        td.when(ctxStub.getElementById('docx-style-package-select')).thenReturn({ value: 'style1' });
        td.when(ctxStub.querySelector("input[name='scope']")).thenReturn({ value: 'global' });

        panel.stylePackageChanged();

        td.verify(ctxStub.callAsync(td.matchers.isA(Object)));
    });
});
