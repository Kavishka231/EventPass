export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

export type ApiErrorKind =
  | 'validation'
  | 'authentication'
  | 'authorization'
  | 'not-found'
  | 'conflict'
  | 'rate-limit'
  | 'server'
  | 'timeout'
  | 'network'
  | 'cancelled'
  | 'unexpected';

export interface BackendErrorResponse {
  timestamp: string;
  status: number;
  code: string;
  message: string;
  path: string;
  requestId: string | null;
}

export interface ApiResponse<T> {
  data: T;
  status: number;
  requestId: string | null;
  correlationId: string | null;
  rateLimit: RateLimitMetadata | null;
}

export interface RateLimitMetadata {
  limit: number | null;
  remaining: number | null;
}

export type ResponseDecoder<T> = (value: unknown) => T;

export type QueryPrimitive = string | number | boolean;
export type QueryValue =
  QueryPrimitive | readonly QueryPrimitive[] | null | undefined;
export type QueryParameters = Readonly<Record<string, QueryValue>>;

export interface RequestConfiguration {
  headers?: HeadersInit;
  query?: QueryParameters;
  signal?: AbortSignal;
  timeoutMs?: number;
  requestId?: string;
  correlationId?: string;
}

export interface AuthenticationTransport {
  getAccessToken(): string | null | Promise<string | null>;
  onUnauthorized?(error: ApiError): void | Promise<void>;
}

export class ApiError extends Error {
  readonly name = 'ApiError';

  constructor(
    message: string,
    readonly status: number,
    readonly code: string,
    readonly kind: ApiErrorKind,
    readonly requestId: string | null,
    readonly correlationId: string | null,
    readonly path: string | null,
    readonly timestamp: string | null,
    readonly retryable: boolean,
    options?: ErrorOptions,
  ) {
    super(message, options);
  }
}
