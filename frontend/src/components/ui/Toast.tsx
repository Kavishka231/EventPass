import type { ReactNode } from 'react';

import { Button } from './Button';

type ToastTone = 'success' | 'error' | 'info';

export function ToastViewport({ children }: { children: ReactNode }) {
  return (
    <div
      className="toast-viewport"
      aria-live="polite"
      aria-relevant="additions"
    >
      {children}
    </div>
  );
}

export function Toast({
  children,
  onDismiss,
  title,
  tone = 'info',
}: {
  children?: ReactNode;
  onDismiss?: () => void;
  title: string;
  tone?: ToastTone;
}) {
  return (
    <div
      className="toast"
      data-tone={tone}
      role={tone === 'error' ? 'alert' : 'status'}
    >
      <div>
        <strong>{title}</strong>
        {children ? <div>{children}</div> : null}
      </div>
      {onDismiss ? (
        <Button
          variant="ghost"
          size="small"
          aria-label="Dismiss notification"
          onClick={onDismiss}
        >
          ×
        </Button>
      ) : null}
    </div>
  );
}
