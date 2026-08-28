import {
  ApiError,
  type ApiErrorKind,
  type BackendErrorResponse,
} from '../../types';

interface ErrorContext {
  status?: number;
  body?: unknown;
  requestId?: string | null;
  correlationId?: string | null;
  path?: string | null;
  timedOut?: boolean;
  cancelled?: boolean;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isNullableString(value: unknown): value is string | null {
  return typeof value === 'string' || value === null;
}

export function isBackendErrorResponse(
  value: unknown,
): value is BackendErrorResponse {
  if (!isRecord(value)) return false;

  return (
    typeof value.timestamp === 'string' &&
    typeof value.status === 'number' &&
    typeof value.code === 'string' &&
    typeof value.message === 'string' &&
    typeof value.path === 'string' &&
    isNullableString(value.requestId)
  );
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError;
}

export function isRetryableStatus(status: number) {
  return (
    status === 0 ||
    status === 408 ||
    status === 425 ||
    [500, 502, 503, 504].includes(status)
  );
}

function kindForStatus(status: number): ApiErrorKind {
  if (status === 400 || status === 422) return 'validation';
  if (status === 401) return 'authentication';
  if (status === 403) return 'authorization';
  if (status === 404) return 'not-found';
  if (status === 409) return 'conflict';
  if (status === 429) return 'rate-limit';
  if (status >= 500) return 'server';
  if (status === 0) return 'network';
  return 'unexpected';
}

function fallbackMessage(status: number) {
  if (status === 400 || status === 422)
    return 'The request contains invalid information.';
  if (status === 401) return 'Your session is missing or has expired.';
  if (status === 403)
    return 'You do not have permission to perform this operation.';
  if (status === 404) return 'The requested resource was not found.';
  if (status === 409)
    return 'The request conflicts with the current resource state.';
  if (status === 429) return 'Too many requests were made. Try again later.';
  if (status >= 500)
    return 'EventPass is temporarily unable to complete the request.';
  return 'The request could not be completed.';
}

export function normalizeApiError(error: unknown, context: ErrorContext = {}) {
  if (isApiError(error)) return error;

  if (context.timedOut) {
    return new ApiError(
      'The request timed out. Check your connection and try again.',
      0,
      'REQUEST_TIMEOUT',
      'timeout',
      context.requestId ?? null,
      context.correlationId ?? null,
      context.path ?? null,
      null,
      true,
      { cause: error },
    );
  }

  if (context.cancelled) {
    return new ApiError(
      'The request was cancelled.',
      0,
      'REQUEST_CANCELLED',
      'cancelled',
      context.requestId ?? null,
      context.correlationId ?? null,
      context.path ?? null,
      null,
      false,
      { cause: error },
    );
  }

  const backendError = isBackendErrorResponse(context.body)
    ? context.body
    : null;
  const status = backendError?.status ?? context.status ?? 0;

  return new ApiError(
    backendError?.message ?? fallbackMessage(status),
    status,
    backendError?.code ?? (status === 0 ? 'NETWORK_ERROR' : `HTTP_${status}`),
    kindForStatus(status),
    backendError?.requestId ?? context.requestId ?? null,
    context.correlationId ?? null,
    backendError?.path ?? context.path ?? null,
    backendError?.timestamp ?? null,
    isRetryableStatus(status),
    { cause: error },
  );
}
