import ExtensionContext from "/polarion/docx-exporter/ui/generic/js/modules/ExtensionContext.js";
import ExportParams from "./ExportParams.js";

export default class ExportContext extends ExtensionContext {
    static PULL_INTERVAL = 1000;
    projectId = undefined;
    locationPath = undefined;
    baselineRevision = undefined;
    revision = undefined;
    urlQueryParameters = undefined;

    constructor({
                    polarionLocationHash = window.location.hash,
                    rootComponentSelector
                }) {
        const urlPathAndSearchParams = getPathAndQueryParams(polarionLocationHash);
        const normalizedPolarionLocationHash = urlPathAndSearchParams.path;
        const scope = getScope(normalizedPolarionLocationHash);
        super({extension: "docx-exporter", setting: "docx-exporter", rootComponentSelector: rootComponentSelector, scope: scope});

        const baseline = getBaseline(normalizedPolarionLocationHash);
        if (baseline) {
            this.baselineRevision = getBaselineRevision(baseline);
        }

        this.projectId = getProjectId(scope);
        this.locationPath = getPath(normalizedPolarionLocationHash, scope);

        const searchParameters = urlPathAndSearchParams.searchParameters;
        this.urlQueryParameters = getQueryParams(searchParameters);
        this.revision = this.urlQueryParameters?.revision;

        function getPathAndQueryParams(polarionLocationHash) {
            const result = {
                path: undefined,
                searchParameters: undefined
            };

            if (polarionLocationHash.includes("?")) {
                const pathAndQueryParams = decodeURI(polarionLocationHash.substring(2));
                const pathAndQueryParamsArray = pathAndQueryParams.split("?");
                result.path = pathAndQueryParamsArray[0];
                result.searchParameters = pathAndQueryParamsArray[1];
            } else {
                result.path = decodeURI(polarionLocationHash.substring(2));
            }

            return result;
        }

        function getBaseline(locationHash) {
            const baselinePattern = /baseline\/([^/]+)\//;
            const baselineMatch = baselinePattern.exec(locationHash);
            return baselineMatch ? `baseline/${baselineMatch[1]}/` : undefined;
        }

        function getBaselineRevision(baselineScope) {
            const foundValues = /baseline\/(.*)\//.exec(baselineScope);
            return foundValues !== null ? foundValues[1] : null;
        }

        function getScope(locationHash) {
            const projectPattern = /project\/([^/]+)\//;
            const projectMatch = projectPattern.exec(locationHash);
            return projectMatch ? `project/${projectMatch[1]}/` : "";
        }

        function getProjectId(scope) {
            const foundValues = /project\/(.*)\//.exec(scope);
            return foundValues !== null ? foundValues[1] : null;
        }

        function getPath(locationHash, scope) {
            if (scope) {
                const pathPattern = /project\/(.+)\/(wiki\/([^?#]+)|testruns|testrun)/;
                const pathMatch = pathPattern.exec(locationHash);
                const extractedPath = pathMatch ? (pathMatch[3] || pathMatch[2]) : undefined;
                return pathMatch ? addDefaultSpaceIfRequired(extractedPath) : undefined;
            } else {
                const globalPathPattern = /wiki\/([^/?#]+)/;
                const pathMatch = globalPathPattern.exec(locationHash);
                return pathMatch ? addDefaultSpaceIfRequired(pathMatch[1]) : undefined;
            }
        }

        function addDefaultSpaceIfRequired(extractedPath) {
            if (!extractedPath) {
                return "";
            }
            // if "testrun" or "testruns" is present return undefined
            if (extractedPath.startsWith("testrun")) {
                return extractedPath;
            }
            // if contains a '/' return it as it is
            if (extractedPath.includes("/")) {
                return extractedPath;
            }
            // otherwise, prepend '_default/' to the path
            return `_default/${extractedPath}`;
        }

        function getQueryParams(searchParams) {
            if (!searchParameters) {
                return undefined;
            }

            const urlSearchParams = new URLSearchParams(searchParams);
            return Object.fromEntries([...urlSearchParams]);
        }

    }

    getProjectId() {
        return this.projectId;
    }

    getLocationPath() {
        return this.locationPath;
    }

    getBaselineRevision() {
        return this.baselineRevision;
    }

    getRevision() {
        return this.revision;
    }

    getUrlQueryParameters() {
        return this.urlQueryParameters;
    }

    getScope() {
        return this.projectId ? `project/${this.projectId}/` : "";
    }

    getSpaceId() {
        if (this.locationPath?.includes("/")) {
            const pathParts = this.locationPath.split("/");
            return pathParts && pathParts.length > 0 && pathParts[0];
        } else {
            return undefined;
        }
    }

    getDocumentName() {
        if (this.locationPath?.includes("/")) {
            const pathParts = this.locationPath.split("/");
            return pathParts && pathParts.length > 1 && pathParts[1];
        } else {
            return undefined;
        }
    }

    toExportParams() {
        return new ExportParams.Builder()
            .setProjectId(this.projectId)
            .setLocationPath(this.locationPath)
            .setBaselineRevision(this.baselineRevision)
            .setRevision(this.revision)
            .setUrlQueryParameters(this.urlQueryParameters)
            .build();
    }

    async asyncConvertDoc(request, successCallback, errorCallback) {
        this.callAsync({
            method: "POST",
            url: "/polarion/docx-exporter/rest/internal/convert/jobs",
            contentType: "application/json",
            responseType: "blob",
            body: request,
            onOk: (responseText, request) => {
                this.pullAndGetResultDoc(request.getResponseHeader("Location"), successCallback, errorCallback);
            },
            onError: (status, errorMessage, request) => {
                errorCallback(request.response);
            }
        });
    }

    async pullAndGetResultDoc(url, successCallback, errorCallback) {
        await new Promise(resolve => setTimeout(resolve, ExportContext.PULL_INTERVAL));
        this.callAsync({
            method: "GET",
            url: url,
            responseType: "blob",
            onOk: (responseText, request) => {
                if (request.status === 202) {
                    console.log('Async DOCX conversion: still in progress, retrying...');
                    this.pullAndGetResultDoc(url, successCallback, errorCallback);
                } else if (request.status === 200) {
                    let warningMessage;
                    let count = request.getResponseHeader("Missing-WorkItem-Attachments-Count");
                    if (count > 0) {
                        let attachment = request.getResponseHeader("WorkItem-IDs-With-Missing-Attachment")
                        warningMessage = `${count} image(s) in WI(s) ${attachment} were not exported. They were replaced with an image containing 'This image is not accessible'.`;
                    }
                    successCallback({
                        response: request.response,
                        warning: warningMessage
                    });
                }
            },
            onError: (status, errorMessage, request) => {
                errorCallback(request.response);
            }
        });
    }
}
