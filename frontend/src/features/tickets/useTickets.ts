import { keepPreviousData, useQuery } from '@tanstack/react-query';

import { queryKeys } from '../../services/query';
import { ticketService } from './ticketService';

export function useTickets(page: number, size: number) {
  const parameters = { page, size, sort: 'issuedAt,desc' } as const;
  return useQuery({
    queryKey: queryKeys.tickets.list(parameters),
    queryFn: () => ticketService.list(parameters),
    placeholderData: keepPreviousData,
  });
}
