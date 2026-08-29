import type { EventSeatResponse } from '../../types';

export function seatDisplayName(seat: EventSeatResponse) {
  return `${seat.section}${seat.row}-${seat.number}`;
}
