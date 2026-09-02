import { apiClient, objectResponseDecoder } from '../../services/api';
import type { AuthResponse, LoginRequest, RegisterRequest } from '../../types';

const roles = new Set<AuthResponse['role']>(['CUSTOMER', 'ORGANIZER', 'ADMIN']);

function requiredString(object: Record<string, unknown>, field: string) {
  const value = object[field];
  if (typeof value !== 'string' || value.length === 0) {
    throw new Error(`Expected ${field} to be a non-empty string.`);
  }
  return value;
}

function authResponseDecoder(value: unknown): AuthResponse {
  const object = objectResponseDecoder(value);
  const role = requiredString(object, 'role');
  if (!roles.has(role as AuthResponse['role'])) {
    throw new Error('Expected role to be a supported EventPass role.');
  }

  return {
    accessToken: requiredString(object, 'accessToken'),
    tokenType: requiredString(object, 'tokenType'),
    role: role as AuthResponse['role'],
  };
}

export const authService = {
  async login(request: LoginRequest, signal?: AbortSignal) {
    const response = await apiClient.post(
      '/auth/login',
      request,
      authResponseDecoder,
      { authentication: 'omit', signal },
    );
    return response.data;
  },

  async register(request: RegisterRequest, signal?: AbortSignal) {
    const response = await apiClient.post(
      '/auth/register',
      request,
      authResponseDecoder,
      { authentication: 'omit', signal },
    );
    return response.data;
  },

  async refresh() {
    await ensureCsrfCookie();
    const response = await apiClient.post(
      '/auth/refresh',
      undefined,
      authResponseDecoder,
      { authentication: 'omit', headers: csrfHeader() },
    );
    return response.data;
  },

  async logout() {
    await ensureCsrfCookie();
    await apiClient.post('/auth/logout', undefined, () => undefined, {
      authentication: 'omit',
      headers: csrfHeader(),
    });
  },
};

function csrfToken() {
  return document.cookie
    .split('; ')
    .find((cookie) => cookie.startsWith('XSRF-TOKEN='))
    ?.slice('XSRF-TOKEN='.length);
}

function csrfHeader() {
  const token = csrfToken();
  return token ? { 'X-XSRF-TOKEN': decodeURIComponent(token) } : undefined;
}

async function ensureCsrfCookie() {
  if (csrfToken()) return;
  await apiClient.get('/auth/csrf', () => undefined, {
    authentication: 'omit',
  });
}
