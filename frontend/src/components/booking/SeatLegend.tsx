const states = [
  ['available', 'Available'],
  ['selected', 'Selected'],
  ['held', 'Held by another customer'],
  ['sold', 'Sold'],
  ['blocked', 'Blocked'],
] as const;

export function SeatLegend() {
  return (
    <ul className="seat-legend" aria-label="Seat status legend">
      {states.map(([state, label]) => (
        <li key={state}>
          <span
            className="seat-legend-mark"
            data-state={state}
            aria-hidden="true"
          />
          {label}
        </li>
      ))}
    </ul>
  );
}
