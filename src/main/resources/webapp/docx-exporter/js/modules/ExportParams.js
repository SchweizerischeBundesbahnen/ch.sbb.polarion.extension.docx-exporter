export default class ExportParams {

    constructor(builder) {
        this.projectId = builder.projectId;
        this.locationPath = builder.locationPath;
        this.baselineRevision = builder.baselineRevision;
        this.revision = builder.revision;
        this.template = builder.template;
        this.localization = builder.localization;
        this.webhooks = builder.webhooks;
        this.enableCommentsRendering = builder.enableCommentsRendering;
        this.cutEmptyChapters = builder.cutEmptyChapters;
        this.cutEmptyWIAttributes = builder.cutEmptyWIAttributes;
        this.cutLocalUrls = builder.cutLocalUrls;
        this.chapters = builder.chapters;
        this.language = builder.language;
        this.linkedWorkitemRoles = builder.linkedWorkitemRoles;
        this.fileName = builder.fileName;
        this.urlQueryParameters = builder.urlQueryParameters;
        this.internalContent = builder.internalContent;
    }

    toJSON() {
        const filteredObject = Object.keys(this)
            .filter(key => this[key] !== undefined && this[key] !== null)
            .reduce((obj, key) => {
                obj[key] = this[key];
                return obj;
            }, {});
        return JSON.stringify(filteredObject, null, 2);
    }

    static get Builder() {
        return class {
            constructor() {
                this.documentType = 'LIVE_DOC';

                // initialize all other values as undefined
                this.projectId = undefined;
                this.locationPath = undefined;
                this.baselineRevision = undefined;
                this.revision = undefined;
                this.template = undefined;
                this.webhooks = undefined;
                this.enableCommentsRendering = undefined;
                this.cutEmptyChapters = undefined;
                this.cutEmptyWIAttributes = undefined;
                this.cutLocalUrls = undefined;
                this.chapters = undefined;
                this.language = undefined;
                this.linkedWorkitemRoles = undefined;
                this.fileName = undefined;
                this.urlQueryParameters = undefined;
                this.internalContent = undefined;
            }

            setProjectId(projectId) {
                this.projectId = projectId;
                return this;
            }

            setLocationPath(locationPath) {
                this.locationPath = locationPath;
                return this;
            }

            setBaselineRevision(baselineRevision) {
                this.baselineRevision = baselineRevision;
                return this;
            }

            setRevision(revision) {
                this.revision = revision;
                return this;
            }

            setTemplate(template) {
                this.template = template;
                return this;
            }

            setLocalization(localization) {
                this.localization = localization;
                return this;
            }

            setWebhooks(webhooks) {
                this.webhooks = webhooks;
                return this;
            }


            setEnableCommentsRendering(enableCommentsRendering) {
                this.enableCommentsRendering = enableCommentsRendering;
                return this;
            }

            setCutEmptyChapters(cutEmptyChapters) {
                this.cutEmptyChapters = cutEmptyChapters;
                return this;
            }

            setCutEmptyWIAttributes(cutEmptyWIAttributes) {
                this.cutEmptyWIAttributes = cutEmptyWIAttributes;
                return this;
            }

            setCutLocalUrls(cutLocalUrls) {
                this.cutLocalUrls = cutLocalUrls;
                return this;
            }

            setChapters(chapters) {
                this.chapters = chapters;
                return this;
            }

            setLanguage(language) {
                this.language = language;
                return this;
            }

            setLinkedWorkitemRoles(linkedWorkitemRoles) {
                this.linkedWorkitemRoles = linkedWorkitemRoles;
                return this;
            }

            setFileName(fileName) {
                this.fileName = fileName;
                return this;
            }

            setUrlQueryParameters(urlQueryParameters) {
                this.urlQueryParameters = urlQueryParameters;
                return this;
            }

            build() {
                return new ExportParams(this);
            }
        };
    }
}
