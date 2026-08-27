import type { MouseEventHandler } from 'react';
import { NavLink } from 'react-router-dom';

export interface NavigationItem {
  label: string;
  to: string;
  end?: boolean;
}

export function NavigationLinks({
  items,
  onNavigate,
}: {
  items: NavigationItem[];
  onNavigate?: MouseEventHandler<HTMLAnchorElement>;
}) {
  return (
    <ul className="navigation-list">
      {items.map((item) => (
        <li key={item.to}>
          <NavLink
            end={item.end}
            to={item.to}
            onClick={onNavigate}
            className={({ isActive }) =>
              isActive ? 'navigation-link is-active' : 'navigation-link'
            }
          >
            {item.label}
          </NavLink>
        </li>
      ))}
    </ul>
  );
}
