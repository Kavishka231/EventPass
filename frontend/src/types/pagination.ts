export interface SortMetadata {
  empty: boolean;
  sorted: boolean;
  unsorted: boolean;
}

export interface PageableMetadata {
  pageNumber: number;
  pageSize: number;
  sort: SortMetadata;
  offset: number;
  paged: boolean;
  unpaged: boolean;
}

export interface PaginatedResponse<T> {
  content: T[];
  pageable: PageableMetadata;
  totalPages: number;
  totalElements: number;
  last: boolean;
  size: number;
  number: number;
  sort: SortMetadata;
  numberOfElements: number;
  first: boolean;
  empty: boolean;
}

export interface PaginationParameters {
  page?: number;
  size?: number;
  sort?: string | readonly string[];
}

export const DEFAULT_PAGE_SIZE = 20;
export const MAX_PAGE_SIZE = 100;
