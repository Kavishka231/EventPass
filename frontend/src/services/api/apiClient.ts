import { environment } from '../../lib/environment';
import { ApiError } from '../../types';
import type {
  ApiResponse,
  AuthenticationTransport,
  HttpMethod,
  RateLimitMetadata,
  RequestConfiguration,
  ResponseDecoder,
} from '../../types';
import { normalizeApiError } from './apiError';
import { buildQueryParams } from './queryParameters';

const DEFAULT_TIMEOUT_MS = 15_000;

interface ApiClientConfiguration {
  baseUrl: string;
  timeoutMs?: number;
  authentication?: AuthenticationTransport;
}

interface InternalRequestConfiguration extends RequestConfiguration {
  body?: unknown;
}

function headerNumber(headers: Headers, name: string) {
  const value = headers.get(name);
  if (value === null) return null;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function rateLimitMetadata(headers: Headers): RateLimitMetadata | null {
  const limit = headerNumber(headers, 'X-RateLimit-Limit');
  const remaining = headerNumber(headers, 'X-RateLimit-Remaining');
  return limit === null && remaining === null ? null : { limit, remaining };
}

function generatedRequestId() {
  return globalThis.crypto?.randomUUID?.();
}

async function readResponseBody(response: Response): Promise<unknown> {
  if (response.status === 204 || response.headers.get('Content-Length') === '0')
    return null;

  const contentType = response.headers.get('Content-Type') ?? '';
  if (!contentType.toLowerCase().includes('application/json')) return null;

  try {
    return await response.json();
  } catch {
    return null;
  }
}

export class ApiClient {
  private authentication?: AuthenticationTransport;
  private readonly timeoutMs: number;

  constructor(
    private readonly baseUrl: string,
    configuration: Omit<ApiClientConfiguration, 'baseUrl'> = {},
  ) {
    this.timeoutMs = configuration.timeoutMs ?? DEFAULT_TIMEOUT_MS;
    this.authentication = configuration.authentication;
  }

  setAuthenticationTransport(
    authentication: AuthenticationTransport | undefined,
  ) {
    this.authentication = authentication;
  }

  get<T>(
    path: string,
    decoder: ResponseDecoder<T>,
    configuration?: RequestConfiguration,
  ) {
    return this.request('GET', path, decoder, configuration);
  }

  post<T>(
    path: string,
    body: unknown,
    decoder: ResponseDecoder<T>,
    configuration?: RequestConfiguration,
  ) {
    return this.request('POST', path, decoder, { ...configuration, body });
  }

  put<T>(
    path: string,
    body: unknown,
    decoder: ResponseDecoder<T>,
    configuration?: RequestConfiguration,
  ) {
    return this.request('PUT', path, decoder, { ...configuration, body });
  }

  patch<T>(
    path: string,
    body: unknown,
    decoder: ResponseDecoder<T>,
    configuration?: RequestConfiguration,
  ) {
    return this.request('PATCH', path, decoder, { ...configuration, body });
  }

  delete<T>(
    path: string,
    decoder: ResponseDecoder<T>,
    configuration?: RequestConfiguration,
  ) {
    return this.request('DELETE', path, decoder, configuration);
  }

  private async request<T>(
    method: HttpMethod,
    path: string,
    decoder: ResponseDecoder<T>,
    configuration: InternalRequestConfiguration = {},
    authenticationRetried = false,
  ): Promise<ApiResponse<T>> {
    const url = this.buildUrl(path, configuration.query);
    const requestId = configuration.requestId ?? generatedRequestId();
    const correlationId = configuration.correlationId ?? requestId;
    const headers = new Headers(configuration.headers);
    const controller = new AbortController();
    let timedOut = false;

    if (headers.has('Authorization')) {
      throw new Error(
        'Authorization headers must be supplied through the authentication transport.',
      );
    }

    headers.set('Accept', 'application/json');
    if (configuration.body !== undefined)
      headers.set('Content-Type', 'application/json');
    if (requestId) headers.set('X-Request-Id', requestId);
    if (correlationId) headers.set('X-Correlation-Id', correlationId);

    let accessToken: string | null | undefined;
    try {
      accessToken =
        configuration.authentication === 'omit'
          ? null
          : await this.authentication?.getAccessToken();
    } catch (error) {
      throw new ApiError(
        'Authentication credentials could not be prepared.',
        0,
        'AUTHENTICATION_TRANSPORT_FAILED',
        'authentication',
        requestId ?? null,
        correlationId ?? null,
        path,
        null,
        false,
        { cause: error },
      );
    }
    if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`);

    const abortFromCaller = () =>
      controller.abort(configuration.signal?.reason);
    if (configuration.signal?.aborted) abortFromCaller();
    configuration.signal?.addEventListener('abort', abortFromCaller, {
      once: true,
    });

    const timeout = globalThis.setTimeout(() => {
      timedOut = true;
      controller.abort();
    }, configuration.timeoutMs ?? this.timeoutMs);

    try {
      const response = await fetch(url, {
        method,
        headers,
        body:
          configuration.body === undefined
            ? undefined
            : JSON.stringify(configuration.body),
        signal: controller.signal,
        credentials: 'omit',
      });
      const body = await readResponseBody(response);
      const responseRequestId =
        response.headers.get('X-Request-Id') ?? requestId ?? null;
      const responseCorrelationId =
        response.headers.get('X-Correlation-Id') ?? correlationId ?? null;

      if (!response.ok) {
        const apiError = normalizeApiError(null, {
          status: response.status,
          body,
          requestId: responseRequestId,
          correlationId: responseCorrelationId,
          path,
        });

        if (
          response.status === 401 &&
          !authenticationRetried &&
          configuration.authentication !== 'omit' &&
          this.authentication?.onUnauthorized
        ) {
          try {
            const recovered =
              await this.authentication.onUnauthorized(apiError);
            if (recovered) {
              return this.request(method, path, decoder, configuration, true);
            }
          } catch {
            // Session cleanup must not replace the authoritative API failure.
          }
        }

        throw apiError;
      }

      let data: T;
      try {
        data = decoder(body);
      } catch (error) {
        throw normalizeApiError(error, {
          status: 500,
          requestId: responseRequestId,
          correlationId: responseCorrelationId,
          path,
        });
      }

      return {
        data,
        status: response.status,
        requestId: responseRequestId,
        correlationId: responseCorrelationId,
        rateLimit: rateLimitMetadata(response.headers),
      };
    } catch (error) {
      throw normalizeApiError(error, {
        requestId: requestId ?? null,
        correlationId: correlationId ?? null,
        path,
        timedOut,
        cancelled: controller.signal.aborted && !timedOut,
      });
    } finally {
      globalThis.clearTimeout(timeout);
      configuration.signal?.removeEventListener('abort', abortFromCaller);
    }
  }

  private buildUrl(path: string, query: RequestConfiguration['query']) {
    if (
      !path.startsWith('/') ||
      path.startsWith('//') ||
      path.includes('..') ||
      path.includes('?') ||
      path.includes('#')
    ) {
      throw new Error(
        'API paths must be root-relative and must provide query values separately.',
      );
    }

    const search = buildQueryParams(query).toString();
    return `${this.baseUrl}${path}${search ? `?${search}` : ''}`;
  }
}

export const apiClient = new ApiClient(environment.apiBaseUrl);
