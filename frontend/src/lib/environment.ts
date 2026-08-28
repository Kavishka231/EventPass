const DEFAULT_API_BASE_URL = '/api/v1';
const API_PATH_SUFFIX = '/api/v1';

function normalizeApiBaseUrl(configuredValue: string | undefined) {
  const value = configuredValue?.trim() || DEFAULT_API_BASE_URL;

  if (value.includes('?') || value.includes('#')) {
    throw new Error(
      'VITE_API_BASE_URL must not include a query string or fragment.',
    );
  }

  if (value.startsWith('/')) {
    if (!value.startsWith(API_PATH_SUFFIX)) {
      throw new Error('A relative VITE_API_BASE_URL must begin with /api/v1.');
    }
    return value.replace(/\/+$/, '');
  }

  let parsed: URL;
  try {
    parsed = new URL(value);
  } catch {
    throw new Error(
      'VITE_API_BASE_URL must be an absolute HTTP(S) URL or a root-relative path.',
    );
  }

  if (
    !['http:', 'https:'].includes(parsed.protocol) ||
    parsed.username ||
    parsed.password
  ) {
    throw new Error(
      'VITE_API_BASE_URL must use HTTP(S) and must not contain credentials.',
    );
  }
  if (!parsed.pathname.replace(/\/+$/, '').endsWith(API_PATH_SUFFIX)) {
    throw new Error('VITE_API_BASE_URL must target the backend /api/v1 path.');
  }

  return value.replace(/\/+$/, '');
}

export const environment = Object.freeze({
  apiBaseUrl: normalizeApiBaseUrl(import.meta.env.VITE_API_BASE_URL),
});
