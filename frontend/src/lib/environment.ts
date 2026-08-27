const DEFAULT_API_BASE_URL = '/api/v1';

export const environment = Object.freeze({
  apiBaseUrl: import.meta.env.VITE_API_BASE_URL?.trim() || DEFAULT_API_BASE_URL,
});
