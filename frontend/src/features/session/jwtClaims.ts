import type { AuthResponse } from '../../types';
import type { UserSession } from './sessionTypes';

function decodeBase64Url(value: string) {
  const normalized = value.replaceAll('-', '+').replaceAll('_', '/');
  return atob(normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '='));
}

export function sessionFromAuthentication(response: AuthResponse): UserSession {
  const segments = response.accessToken.split('.');
  if (segments.length !== 3 || !segments[1]) {
    throw new Error(
      'The authentication response contained an invalid access token.',
    );
  }

  const claims: unknown = JSON.parse(decodeBase64Url(segments[1])) as unknown;
  if (typeof claims !== 'object' || claims === null) {
    throw new Error('The authentication response contained invalid claims.');
  }

  const record = claims as Record<string, unknown>;
  const subject = record.sub;
  const role = record.role;
  if (typeof subject !== 'string' || role !== response.role) {
    throw new Error(
      'The authentication response did not identify a valid session.',
    );
  }

  return { userId: subject, role: response.role };
}
