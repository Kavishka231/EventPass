export function Spinner({
  label = 'Loading',
  size = 'medium',
}: {
  label?: string;
  size?: 'small' | 'medium';
}) {
  return (
    <span className="spinner" data-size={size} role="status">
      <span className="sr-only">{label}</span>
    </span>
  );
}
