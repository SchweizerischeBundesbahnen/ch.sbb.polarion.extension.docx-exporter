/**
 * The one field of the export panel a user can get wrong, validated exactly as the legacy
 * `ExportPanel.js` validated it - same rule, same message.
 *
 * Its own module rather than a helper inside the panel: the DLE toolbar popup offers the same switch
 * and refuses an export on the same entry, so both surfaces answer the question here.
 */

export const CHAPTERS_ERROR = 'Please, provide comma separated list of integer values in chapters field';

/**
 * The chapter numbers to export, or `undefined` when the entry is not a comma separated list of positive
 * integers. Spaces are dropped first, so "1, 2" is as good as "1,2"; a leading zero is not ("01" does not
 * round-trip through parseInt, which is what the legacy check tested for).
 */
export function parseChapters(raw: string | null | undefined): string[] | undefined {
  const chapters = (raw?.replace(/ /g, '') || '').split(',');
  for (const chapter of chapters) {
    const parsed = Number.parseInt(chapter);
    if (Number.isNaN(parsed) || parsed < 1 || String(parsed) !== chapter) {
      return undefined;
    }
  }
  return chapters;
}
