import { expect } from 'chai';
import TestUtils from "./TestUtils.js";
import sinon from 'sinon';
import { JSDOM } from 'jsdom';

/** @type {typeof ExportContext} */
const ExportContext = await TestUtils.loadModule('ExportContext.js');

/** @type {typeof ExportPanel} */
const ExportPanel = await TestUtils.loadModule('ExportPanel.js');

describe('ExportPanel', function () {
    let panel, ctxStub;

    beforeEach(function () {
        const dom = new JSDOM('<!DOCTYPE html><html><body></body></html>', { url: 'http://localhost' });
        global.window = dom.window;
        global.document = dom.window.document;

        // Create a stub instance with all methods stubbed
        const realCtx = new ExportContext({ polarionLocationHash: window.location.hash });
        ctxStub = sinon.stub(realCtx);

        panel = new ExportPanel(ctxStub);
    });

    afterEach(function () {
        sinon.restore();
    });

    it.skip('should initialize event listeners', function () {
        sinon.assert.calledWith(ctxStub.onChange, 'docx-style-package-select', sinon.match.func);
        sinon.assert.calledWith(ctxStub.onClick, 'export-docx', sinon.match.func);
    });

    it.skip('should prepare request JSON correctly', function () {
        ctxStub.getElementById.withArgs('docx-specific-chapters').returns({ checked: false });
        ctxStub.getElementById.withArgs('docx-selected-roles').returns({ checked: false });
        ctxStub.getElementById.withArgs('docx-localization-selector').returns({ value: 'en-US' });
        ctxStub.getElementById.withArgs('docx-enable-comments-rendering').returns({ checked: true });
        ctxStub.getElementById.withArgs('docx-cut-empty-chapters').returns({ checked: false });
        ctxStub.getElementById.withArgs('docx-cut-empty-wi-attributes').returns({ checked: true });
        ctxStub.getElementById.withArgs('docx-cut-urls').returns({ checked: false });
        ctxStub.getElementById.withArgs('docx-filename').returns({ value: 'test.docx' });

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
        ctxStub.getElementById.withArgs('docx-style-package-select').returns({ value: 'style1' });
        ctxStub.querySelector.withArgs("input[name='scope']").returns({ value: 'global' });

        panel.stylePackageChanged();

        sinon.assert.calledWith(ctxStub.callAsync, sinon.match.object);
    });
});
