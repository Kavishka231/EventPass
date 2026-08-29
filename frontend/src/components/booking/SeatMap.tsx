import type { EventSeatResponse, SeatType } from '../../types';

const priceFormatter = new Intl.NumberFormat(undefined, {
  style: 'currency',
  currency: 'LKR',
  maximumFractionDigits: 2,
});

const typeLabels: Record<SeatType, string> = {
  REGULAR: 'Regular',
  PREMIUM: 'Premium',
  VIP: 'VIP',
};

function naturalCompare(left: string, right: string) {
  return left.localeCompare(right, undefined, {
    numeric: true,
    sensitivity: 'base',
  });
}

function groupedSeats(seats: EventSeatResponse[]) {
  const sections = new Map<string, Map<string, EventSeatResponse[]>>();
  for (const seat of seats) {
    const rows =
      sections.get(seat.section) ?? new Map<string, EventSeatResponse[]>();
    const row: EventSeatResponse[] = rows.get(seat.row) ?? [];
    row.push(seat);
    rows.set(seat.row, row);
    sections.set(seat.section, rows);
  }
  return [...sections.entries()]
    .sort(([left], [right]) => naturalCompare(left, right))
    .map(([section, rows]) => ({
      section,
      rows: [...rows.entries()]
        .sort(([left], [right]) => naturalCompare(left, right))
        .map(([row, rowSeats]) => ({
          row,
          seats: rowSeats.sort((left, right) =>
            naturalCompare(left.number, right.number),
          ),
        })),
    }));
}

function stateLabel(seat: EventSeatResponse, selected: boolean) {
  if (selected) return 'selected';
  return seat.availability.toLowerCase();
}

export function SeatMap({
  onToggle,
  seats,
  selectedIds,
}: {
  onToggle: (seat: EventSeatResponse) => void;
  seats: EventSeatResponse[];
  selectedIds: ReadonlySet<string>;
}) {
  return (
    <div className="seat-map" aria-label="Event seat map">
      <div className="seat-map-stage" aria-hidden="true">
        Stage
      </div>
      {groupedSeats(seats).map((section) => (
        <section className="seat-section" key={section.section}>
          <h2>Section {section.section}</h2>
          <div className="seat-rows">
            {section.rows.map((row) => (
              <div className="seat-row" key={row.row}>
                <span className="seat-row-label" aria-hidden="true">
                  {row.row}
                </span>
                <div className="seat-row-controls">
                  {row.seats.map((seat) => {
                    const selected = selectedIds.has(seat.id);
                    const available = seat.availability === 'AVAILABLE';
                    const name = `${seat.section}${seat.row}-${seat.number}`;
                    const state = stateLabel(seat, selected);
                    return (
                      <button
                        className="seat-control"
                        data-state={state}
                        key={seat.id}
                        type="button"
                        disabled={!available}
                        aria-pressed={available ? selected : undefined}
                        aria-label={`Seat ${name}, ${typeLabels[seat.type]}, ${state}${available ? `, ${priceFormatter.format(seat.price)}` : ''}`}
                        title={`Seat ${name} · ${typeLabels[seat.type]} · ${state}`}
                        onClick={() => onToggle(seat)}
                      >
                        <span>{seat.number}</span>
                        <small>{selected ? 'Selected' : state}</small>
                      </button>
                    );
                  })}
                </div>
                <span className="seat-row-label" aria-hidden="true">
                  {row.row}
                </span>
              </div>
            ))}
          </div>
        </section>
      ))}
    </div>
  );
}
