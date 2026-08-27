import { Link } from 'react-router-dom';

export function Brand({ compact = false }: { compact?: boolean }) {
  return (
    <Link className="brand" to="/" aria-label="EventPass home">
      <span className="brand-mark" aria-hidden="true">
        E
      </span>
      {compact ? null : <span>EventPass</span>}
    </Link>
  );
}
