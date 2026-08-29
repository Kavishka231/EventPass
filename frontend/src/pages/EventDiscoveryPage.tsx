import { type FormEvent, useState } from 'react';
import { useSearchParams } from 'react-router-dom';

import { EventCard, EventCardSkeleton } from '../components/events';
import { Container, Grid, Section } from '../components/layout';
import {
  Button,
  EmptyState,
  ErrorState,
  Input,
  Label,
  Select,
} from '../components/ui';
import { useEvents } from '../features/events';
import type { EventSearchParameters } from '../types';

const PAGE_SIZE = 9;
const allowedSorts = new Set([
  'startDateTime,asc',
  'startDateTime,desc',
  'name,asc',
]);

function positivePage(value: string | null) {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : 1;
}

function dateBoundary(value: string, endOfDay = false) {
  if (!value) return undefined;
  const suffix = endOfDay ? 'T23:59:59.999' : 'T00:00:00.000';
  const date = new Date(`${value}${suffix}`);
  return Number.isNaN(date.getTime()) ? undefined : date.toISOString();
}

function searchFromUrl(searchParams: URLSearchParams): EventSearchParameters {
  const sort = searchParams.get('sort') ?? 'startDateTime,asc';
  return {
    category: searchParams.get('category')?.trim() || undefined,
    city: searchParams.get('city')?.trim() || undefined,
    startDate: dateBoundary(searchParams.get('from') ?? ''),
    endDate: dateBoundary(searchParams.get('to') ?? '', true),
    page: positivePage(searchParams.get('page')) - 1,
    size: PAGE_SIZE,
    sort: allowedSorts.has(sort) ? sort : 'startDateTime,asc',
  };
}

export function EventDiscoveryPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const parameters = searchFromUrl(searchParams);
  const events = useEvents(parameters);
  const [filters, setFilters] = useState({
    category: searchParams.get('category') ?? '',
    city: searchParams.get('city') ?? '',
    from: searchParams.get('from') ?? '',
    to: searchParams.get('to') ?? '',
    sort: searchParams.get('sort') ?? 'startDateTime,asc',
  });

  function applyFilters(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const next = new URLSearchParams();
    for (const [key, value] of Object.entries(filters)) {
      if (value.trim()) next.set(key, value.trim());
    }
    setSearchParams(next);
  }

  function clearFilters() {
    setFilters({
      category: '',
      city: '',
      from: '',
      to: '',
      sort: 'startDateTime,asc',
    });
    setSearchParams({});
  }

  function selectPage(page: number) {
    const next = new URLSearchParams(searchParams);
    if (page <= 1) next.delete('page');
    else next.set('page', String(page));
    setSearchParams(next);
    document.querySelector('#event-results')?.scrollIntoView({
      behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches
        ? 'auto'
        : 'smooth',
    });
  }

  const currentPage = (parameters.page ?? 0) + 1;

  return (
    <>
      <Section className="discovery-header">
        <Container>
          <p className="discovery-eyebrow">Published experiences</p>
          <h1>Find your next event</h1>
          <p>
            Search the live EventPass catalogue by category, city, and date.
          </p>
        </Container>
      </Section>

      <Section className="discovery-content">
        <Container>
          <form className="event-filters" onSubmit={applyFilters}>
            <div className="filter-field">
              <Label htmlFor="event-category">Category</Label>
              <Input
                id="event-category"
                value={filters.category}
                placeholder="e.g. Music"
                onChange={(event) =>
                  setFilters((current) => ({
                    ...current,
                    category: event.target.value,
                  }))
                }
              />
            </div>
            <div className="filter-field">
              <Label htmlFor="event-city">City</Label>
              <Input
                id="event-city"
                value={filters.city}
                placeholder="e.g. Colombo"
                onChange={(event) =>
                  setFilters((current) => ({
                    ...current,
                    city: event.target.value,
                  }))
                }
              />
            </div>
            <div className="filter-field">
              <Label htmlFor="event-from">From</Label>
              <Input
                id="event-from"
                type="date"
                value={filters.from}
                onChange={(event) =>
                  setFilters((current) => ({
                    ...current,
                    from: event.target.value,
                  }))
                }
              />
            </div>
            <div className="filter-field">
              <Label htmlFor="event-to">To</Label>
              <Input
                id="event-to"
                type="date"
                min={filters.from || undefined}
                value={filters.to}
                onChange={(event) =>
                  setFilters((current) => ({
                    ...current,
                    to: event.target.value,
                  }))
                }
              />
            </div>
            <div className="filter-field filter-sort">
              <Label htmlFor="event-sort">Sort</Label>
              <Select
                id="event-sort"
                value={filters.sort}
                onChange={(event) =>
                  setFilters((current) => ({
                    ...current,
                    sort: event.target.value,
                  }))
                }
              >
                <option value="startDateTime,asc">Soonest first</option>
                <option value="startDateTime,desc">Latest first</option>
                <option value="name,asc">Name A–Z</option>
              </Select>
            </div>
            <div className="filter-actions">
              <Button type="submit">Apply filters</Button>
              <Button type="button" variant="ghost" onClick={clearFilters}>
                Clear
              </Button>
            </div>
          </form>

          <div className="results-heading" id="event-results" tabIndex={-1}>
            <div>
              <p className="discovery-eyebrow">Event catalogue</p>
              <h2>Available events</h2>
            </div>
            {events.data ? (
              <p aria-live="polite">
                {events.data.totalElements}{' '}
                {events.data.totalElements === 1 ? 'event' : 'events'}
              </p>
            ) : null}
          </div>

          {events.isPending ? (
            <Grid aria-label="Loading events">
              {Array.from({ length: 6 }, (_, index) => (
                <EventCardSkeleton key={index} />
              ))}
            </Grid>
          ) : events.isError ? (
            <ErrorState
              title="Events couldn't be loaded"
              description="Check your connection or try again in a moment."
              actionLabel="Try again"
              onAction={() => void events.refetch()}
            />
          ) : events.data.content.length === 0 ? (
            <EmptyState
              title="No events match these filters"
              description="Try a different category, city, or date range."
              actionLabel="Clear filters"
              onAction={clearFilters}
            />
          ) : (
            <div className={events.isFetching ? 'results-updating' : undefined}>
              <Grid>
                {events.data.content.map((event) => (
                  <EventCard event={event} key={event.id} />
                ))}
              </Grid>
              {events.isFetching ? (
                <p className="results-status" role="status">
                  Updating events…
                </p>
              ) : null}
            </div>
          )}

          {events.data && events.data.totalPages > 1 ? (
            <nav className="pagination" aria-label="Event results pages">
              <Button
                variant="outline"
                disabled={events.data.first || events.isFetching}
                onClick={() => selectPage(currentPage - 1)}
              >
                Previous
              </Button>
              <span aria-live="polite">
                Page {currentPage} of {events.data.totalPages}
              </span>
              <Button
                variant="outline"
                disabled={events.data.last || events.isFetching}
                onClick={() => selectPage(currentPage + 1)}
              >
                Next
              </Button>
            </nav>
          ) : null}
        </Container>
      </Section>
    </>
  );
}
