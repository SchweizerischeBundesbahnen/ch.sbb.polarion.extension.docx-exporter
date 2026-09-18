import { toast } from 'sonner';

/**
 * What an export surface tells the user, and how it reads that out of a failure.
 *
 * The "Export to DOCX" dialog and the Document Properties side panel report the same outcomes - an export
 * that failed, an export that came back with a warning, and one that succeeded - and used to word and place
 * them differently: the dialog showed alert boxes inside its form and the panel plain red and orange text
 * under its button. They report through these functions now, which is `sonner`'s `toast` - the same toasts
 * every administration page of this extension has always used, and the same host (RSP's `Toaster`, see
 * components/ToastHost.tsx).
 *
 * Toasts rather than a block in the form because these are events, not state: the message no longer takes
 * a place in a layout it has to be given room in, cannot be scrolled away from the button that produced it,
 * and is the same message in the same corner whichever surface raised it. What stays in the form is what
 * describes a *state*: the form that could not be loaded.
 */

export const EXPORT_ERROR = 'Error occurred during DOCX generation';
export const EXPORT_SUCCESS = 'DOCX was successfully generated';

/** `<prefix>: <detail>` - the legacy `prefix + ": " + message`, with nothing appended where there is none. */
export const withDetail = (prefix: string, detail: string): string => (detail ? `${prefix}: ${detail}` : prefix);

/** What a rejected read or conversion says, which is the server's message or nothing. */
export const messageOf = (error: unknown): string => (error instanceof Error ? error.message : '');

/**
 * How long a message of each kind stays, and how it can be sent away.
 *
 * A failure waits to be dismissed, which is what the alert box it replaced did: it names something the user
 * has to read, often a server message, and an export dialog is open in front of them while they do. A
 * warning is a conversion that produced a file with something to know about it, so it outstays the 5s an
 * administration page's "Data successfully saved." gets, but it does go. A success needs no more than that
 * 5s default.
 *
 * All three carry a close button. A failure has to have one - nothing else would take it off the screen -
 * and the other two are given one so that a reader who is done with a message never has to wait for it,
 * whichever kind it is.
 */
const FAILURE = { duration: Infinity, closeButton: true } as const;
const WARNING = { duration: 20_000, closeButton: true } as const;
const SUCCESS = { closeButton: true } as const;

/** Reports a failed operation, with whatever the server said about it. */
export const reportFailure = (prefix: string, failure: unknown): void => {
  toast.error(withDetail(prefix, messageOf(failure)), FAILURE);
};

/** Reports an operation refused before it started, whose message names the field that is wrong. */
export const reportRefusal = (message: string): void => {
  toast.error(message, FAILURE);
};

/** Reports something to know about a file that was produced all the same. */
export const reportWarning = (message: string): void => {
  toast.warning(message, WARNING);
};

export const reportSuccess = (message: string): void => {
  toast.success(message, SUCCESS);
};

/** Takes back whatever was last reported, which is what an operation does before it starts another. */
export const clearReports = (): void => {
  toast.dismiss();
};
