import type {
  PaginatedResponse,
  PageableMetadata,
  ResponseDecoder,
  SortMetadata,
} from '../../types';

export const unknownResponseDecoder: ResponseDecoder<unknown> = (value) =>
  value;

export const emptyResponseDecoder: ResponseDecoder<void> = (value) => {
  if (value !== null) throw new Error('Expected an empty response body.');
};

export function objectResponseDecoder(value: unknown): Record<string, unknown> {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) {
    throw new Error('Expected a JSON object response.');
  }
  return Object.fromEntries(Object.entries(value));
}

function booleanField(value: Record<string, unknown>, field: string) {
  const candidate = value[field];
  if (typeof candidate !== 'boolean')
    throw new Error(`Expected ${field} to be a boolean.`);
  return candidate;
}

function numberField(value: Record<string, unknown>, field: string) {
  const candidate = value[field];
  if (typeof candidate !== 'number' || !Number.isFinite(candidate)) {
    throw new Error(`Expected ${field} to be a finite number.`);
  }
  return candidate;
}

function decodeSort(value: unknown): SortMetadata {
  const object = objectResponseDecoder(value);
  return {
    empty: booleanField(object, 'empty'),
    sorted: booleanField(object, 'sorted'),
    unsorted: booleanField(object, 'unsorted'),
  };
}

function decodePageable(value: unknown): PageableMetadata {
  const object = objectResponseDecoder(value);
  return {
    pageNumber: numberField(object, 'pageNumber'),
    pageSize: numberField(object, 'pageSize'),
    sort: decodeSort(object.sort),
    offset: numberField(object, 'offset'),
    paged: booleanField(object, 'paged'),
    unpaged: booleanField(object, 'unpaged'),
  };
}

export function paginatedResponseDecoder<T>(
  itemDecoder: ResponseDecoder<T>,
): ResponseDecoder<PaginatedResponse<T>> {
  return (value) => {
    const object = objectResponseDecoder(value);
    if (!Array.isArray(object.content))
      throw new Error('Expected content to be an array.');

    return {
      content: object.content.map(itemDecoder),
      pageable: decodePageable(object.pageable),
      totalPages: numberField(object, 'totalPages'),
      totalElements: numberField(object, 'totalElements'),
      last: booleanField(object, 'last'),
      size: numberField(object, 'size'),
      number: numberField(object, 'number'),
      sort: decodeSort(object.sort),
      numberOfElements: numberField(object, 'numberOfElements'),
      first: booleanField(object, 'first'),
      empty: booleanField(object, 'empty'),
    };
  };
}
