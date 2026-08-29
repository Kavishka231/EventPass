import { ApiError } from '../../types';

export interface AuthenticationErrorPresentation {
  message: string;
  field?: 'email';
}

export function presentAuthenticationError(
  error: unknown,
): AuthenticationErrorPresentation {
  if (!(error instanceof ApiError)) {
    return { message: 'Something went wrong. Please try again.' };
  }

  if (error.code === 'INVALID_CREDENTIALS') {
    return { message: 'Invalid email or password.' };
  }
  if (error.code === 'EMAIL_EXISTS') {
    return {
      field: 'email',
      message: 'An account with this email already exists.',
    };
  }
  if (error.kind === 'rate-limit') {
    return { message: 'Too many attempts. Please try again later.' };
  }
  if (error.kind === 'network' || error.kind === 'timeout') {
    return { message: "We couldn't connect to the server. Please try again." };
  }
  if (error.kind === 'server') {
    return {
      message: 'EventPass is temporarily unavailable. Please try again.',
    };
  }
  if (error.kind === 'validation') {
    return { message: 'Check the highlighted details and try again.' };
  }

  return { message: error.message || 'Your request could not be completed.' };
}
