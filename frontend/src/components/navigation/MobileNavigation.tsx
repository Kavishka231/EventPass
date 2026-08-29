import { useEffect, useId, useRef, useState } from 'react';
import { Link } from 'react-router-dom';

import { Button } from '../ui';
import { NavigationLinks, type NavigationItem } from './NavigationLinks';

interface MobileNavigationProps {
  items: NavigationItem[];
  primaryAction?: { label: string; to: string };
  secondaryAction?: { label: string; to: string };
  sessionAction?: { label: string; onAction: () => void | Promise<void> };
}

export function MobileNavigation({
  items,
  primaryAction,
  secondaryAction,
  sessionAction,
}: MobileNavigationProps) {
  const [open, setOpen] = useState(false);
  const dialogRef = useRef<HTMLDialogElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const menuId = useId();
  const titleId = useId();

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;

    if (open && !dialog.open) {
      dialog.showModal();
      dialog.querySelector<HTMLAnchorElement>('a')?.focus();
    }

    if (!open && dialog.open) dialog.close();
  }, [open]);

  const close = (restoreFocus = false) => {
    setOpen(false);
    if (restoreFocus) requestAnimationFrame(() => triggerRef.current?.focus());
  };

  return (
    <div className="mobile-navigation">
      <Button
        ref={triggerRef}
        variant="ghost"
        size="small"
        aria-controls={menuId}
        aria-expanded={open}
        aria-haspopup="dialog"
        aria-label="Open navigation menu"
        onClick={() => setOpen(true)}
      >
        Menu
      </Button>
      <dialog
        id={menuId}
        ref={dialogRef}
        className="mobile-navigation-dialog"
        aria-labelledby={titleId}
        onCancel={(event) => {
          event.preventDefault();
          close(true);
        }}
        onClose={() => setOpen(false)}
        onClick={(event) => {
          if (event.target === event.currentTarget) close(true);
        }}
      >
        <div className="mobile-navigation-surface">
          <header>
            <strong id={titleId}>Navigation</strong>
            <Button
              variant="ghost"
              size="small"
              aria-label="Close navigation menu"
              onClick={() => close(true)}
            >
              ×
            </Button>
          </header>
          <nav aria-label="Mobile navigation">
            <NavigationLinks items={items} onNavigate={() => close()} />
          </nav>
          {primaryAction || secondaryAction || sessionAction ? (
            <div className="mobile-navigation-actions">
              {secondaryAction ? (
                <Link
                  className="button link-button"
                  data-size="medium"
                  data-variant="outline"
                  to={secondaryAction.to}
                  onClick={() => close()}
                >
                  {secondaryAction.label}
                </Link>
              ) : null}
              {primaryAction ? (
                <Link
                  className="button link-button"
                  data-size="medium"
                  data-variant="primary"
                  to={primaryAction.to}
                  onClick={() => close()}
                >
                  {primaryAction.label}
                </Link>
              ) : null}
              {sessionAction ? (
                <Button
                  variant="outline"
                  onClick={() => {
                    close();
                    void sessionAction.onAction();
                  }}
                >
                  {sessionAction.label}
                </Button>
              ) : null}
            </div>
          ) : null}
        </div>
      </dialog>
    </div>
  );
}
