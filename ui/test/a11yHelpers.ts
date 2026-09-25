import { expect, vi } from 'vitest';

/**
 * Waits until every `<select>` under `root` has become a SearchableSelect. The upgrade runs in an effect,
 * and the native `<select>` it replaces is correctly labelled, so a scan taken before it passes for nothing.
 */
export const dropdownsSettled = (root: ParentNode = document) =>
  vi.waitFor(() =>
    expect(root.querySelectorAll('.searchable-dropdown').length).toBe(root.querySelectorAll('select').length),
  );
