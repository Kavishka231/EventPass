import type { AuthResponse } from '../../types';

let credentials: AuthResponse | null = null;

export const credentialVault = {
  clear() {
    credentials = null;
  },
  read() {
    return credentials;
  },
  replace(next: AuthResponse) {
    credentials = next;
  },
};
