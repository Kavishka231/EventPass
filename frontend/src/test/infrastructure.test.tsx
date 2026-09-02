import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { Button } from '../components/ui';
import { eventService } from '../features/events/eventService';
import { renderWithApp } from './render';

describe('frontend test infrastructure', () => {
  it('renders through the shared router and query wrapper', () => {
    renderWithApp(<Button>Continue</Button>);
    expect(screen.getByRole('button', { name: 'Continue' })).toBeVisible();
  });

  it('uses MSW responses that satisfy production runtime decoders', async () => {
    const events = await eventService.list({ page: 0, size: 20 });
    expect(events.content[0]?.name).toBe('Colombo Jazz Evening');
  });
});
