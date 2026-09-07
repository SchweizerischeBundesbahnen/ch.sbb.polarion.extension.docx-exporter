import { useEffect, useState } from 'react';
import { Toaster } from '@sbb-polarion/react-sbb-polarion';
import { toast } from 'sonner';

/**
 * Where a toast appears: the shared RSP `Toaster`, and only one of them at a time.
 *
 * An administration page needs no such thing - it mounts one `Toaster` at the app root and is done. The
 * export surfaces cannot: each of them lives in a shadow root of its own, which sees none of the rules
 * sonner puts in the document, and the export dialog is a native `<dialog>` in the browser's top layer,
 * which paints above everything in the normal layer whatever its z-index - so a host outside that dialog
 * would report *behind* it, under its backdrop. Each surface therefore renders a host of its own, the
 * dialog's inside the dialog.
 *
 * Which is a problem, because `toast()` is a module singleton that broadcasts to **every** mounted
 * `Toaster`, and two of these surfaces are on one page whenever a document is open in the editor: the
 * Document Properties side panel, and the dialog the toolbar button opens over it. Both hosts would show
 * every message - twice, in the same place, one of the two behind the backdrop.
 *
 * So the hosts take turns, and which of them reports is decided by the surface it belongs to and not by
 * the order the hosts happened to mount in. The dialog is the one surface a toast can be read on while it
 * is open, so it outranks the rest for as long as it is there. Mount order is the tie-breaker only, for
 * two hosts of the same surface.
 *
 * Mount order alone would be wrong, and not only in theory: the panel renders its host below its own
 * loading state, so a dialog opened while the properties pane still reads `Loading...` would be pushed
 * aside the moment those reads came back - and every report of that export would go to the pane behind
 * the backdrop. The same flip happens to a dialog opened before the app's host mounts in the development
 * harness, and to any surface Polarion re-creates while a dialog is open.
 *
 * A change of hands empties the queue, and that is the second thing this does. Sonner replays every toast
 * still active to a `Toaster` that has just subscribed - which is how a report raised while its host was
 * still mounting is not lost - and between these surfaces that replay carries one surface's report onto
 * another: the dialog would open showing the failure the panel reported before it, and closing the dialog
 * would move its own report, a "DOCX was successfully generated" the user has already read, into the
 * properties pane behind it. A report belongs to the surface that made it, so it goes when the surface
 * reporting changes. Nothing else empties it - a host mounting or leaving without taking the reporting
 * over, which is what the panel does while a dialog is open, leaves what is on screen alone.
 */

/** The surfaces that report, and which of them wins where more than one is on the page. */
export type ToastSurface = 'app' | 'panel' | 'dialog';

const RANK: Record<ToastSurface, number> = { app: 0, panel: 1, dialog: 2 };

interface Host {
  readonly rank: number;
}

/** The mounted hosts, oldest first. Module scope on purpose - this is exactly what has to be shared. */
let hosts: Host[] = [];
const listeners = new Set<() => void>();

/** The host that reports: the highest-ranked one, the newest of them where several rank alike. */
const reporter = (): Host | undefined =>
  hosts.reduce<Host | undefined>((best, host) => (best && best.rank > host.rank ? best : host), undefined);

/** The host {@link announce} last handed the reporting to, so a change of hands can be recognized. */
let reporting: Host | undefined;

const announce = () => {
  const next = reporter();
  if (next !== reporting) {
    // Before the listeners, so the `Toaster` mounting next is handed nothing - see above. Not on the
    // first host, whose queue holds what was raised for its own surface while it was mounting.
    if (reporting) {
      toast.dismiss();
    }
    reporting = next;
  }
  listeners.forEach((listener) => listener());
};

export interface ToastHostProps {
  /**
   * The surface this host belongs to, which is what it reports for. Read once - a host stays the surface
   * it was mounted as.
   */
  surface?: ToastSurface;
}

export default function ToastHost({ surface = 'app' }: Readonly<ToastHostProps>) {
  /** This host's identity and rank, stable across renders. */
  const [host] = useState<Host>(() => ({ rank: RANK[surface] }));
  const [active, setActive] = useState(false);

  useEffect(() => {
    const update = () => setActive(reporter() === host);
    hosts = [...hosts, host];
    listeners.add(update);
    // Every host is told, this one included: the reporting may have just moved.
    announce();
    return () => {
      hosts = hosts.filter((mounted) => mounted !== host);
      listeners.delete(update);
      announce();
    };
  }, [host]);

  // `expand`, because one operation can report twice: a conversion that produced a file *and* had something
  // to say about it raises a warning and a success. Sonner stacks its toasts by default - the newest in
  // front, the rest scaled down behind it with their text hidden until the pointer is over them - so the
  // "DOCX was successfully generated" would all but cover the warning it belongs with. Expanded, each is laid
  // out under the one before it and both are read at once.
  return active ? <Toaster expand /> : null;
}
