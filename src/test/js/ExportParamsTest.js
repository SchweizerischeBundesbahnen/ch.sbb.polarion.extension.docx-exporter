import { expect } from 'chai';
import TestUtils from "./TestUtils.js";

/** @type {typeof ExportParams} */
const ExportParams = await TestUtils.loadModule('ExportParams.js');

describe('ExportParams', function () {
    it('should correctly assign values from the builder', function () {
        const params = new ExportParams.Builder()
            .setProjectId('12345')
            .setLocationPath('/some/path')
            .setBaselineRevision(10)
            .setRevision(20)
            .setLocalization('en-US')
            .setPaperSize('A3')
            .setOrientation('Landscape')
            .setWebhooks(['webhook1', 'webhook2'])
            .setRenderComments('ALL')
            .setCutEmptyChapters(false)
            .setCutEmptyWIAttributes(true)
            .setCutLocalUrls(false)
            .setChapters(['chapter1', 'chapter2'])
            .setLanguage('English')
            .setLinkedWorkitemRoles(['role1', 'role2'])
            .setFileName('document.pdf')
            .setUrlQueryParameters({ key: 'value' })
            .build();

        expect(params.projectId).to.equal('12345');
        expect(params.locationPath).to.equal('/some/path');
        expect(params.baselineRevision).to.equal(10);
        expect(params.revision).to.equal(20);
        expect(params.localization).to.equal('en-US');
        expect(params.paperSize).to.equal('A3');
        expect(params.orientation).to.equal('Landscape');
        expect(params.webhooks).to.deep.equal(['webhook1', 'webhook2']);
        expect(params.renderComments).to.equal('ALL');
        expect(params.cutEmptyChapters).to.be.false;
        expect(params.cutEmptyWIAttributes).to.be.true;
        expect(params.cutLocalUrls).to.be.false;
        expect(params.chapters).to.deep.equal(['chapter1', 'chapter2']);
        expect(params.language).to.equal('English');
        expect(params.linkedWorkitemRoles).to.deep.equal(['role1', 'role2']);
        expect(params.fileName).to.equal('document.pdf');
        expect(params.urlQueryParameters).to.deep.equal({ key: 'value' });
    });

    it('should convert to JSON correctly', function () {
        const params = new ExportParams.Builder()
            .setProjectId('12345')
            .setLocationPath('/some/path')
            .setRevision(20)
            .build();

        const expectedJson = JSON.stringify({
            projectId: '12345',
            locationPath: '/some/path',
            revision: 20,
        }, null, 2);

        expect(params.toJSON()).to.equal(expectedJson);
    });

    it('should exclude undefined properties in JSON output', function () {
        const params = new ExportParams.Builder()
            .setProjectId('67890')
            .build();

        const expectedJson = JSON.stringify({ projectId: '67890' }, null, 2);
        expect(params.toJSON()).to.equal(expectedJson);
    });
});
