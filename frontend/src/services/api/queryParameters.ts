import type { QueryParameters } from '../../types';

export function buildQueryParams(parameters: QueryParameters | undefined) {
  const search = new URLSearchParams();

  if (!parameters) return search;

  for (const [key, value] of Object.entries(parameters)) {
    if (value === null || value === undefined || value === '') continue;

    if (Array.isArray(value)) {
      for (const item of value) search.append(key, String(item));
    } else {
      search.set(key, String(value));
    }
  }

  return search;
}
