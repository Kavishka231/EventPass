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
    refreshToken: requiredString(object, 'refreshToken'),
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
      { signal },
    );
    return response.data;
  },

  async register(request: RegisterRequest, signal?: AbortSignal) {
    const response = await apiClient.post(
      '/auth/register',
      request,
      authResponseDecoder,
      { signal },
    );
    return response.data;
  },
};
