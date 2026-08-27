import type { HTMLAttributes, ReactNode } from 'react';
import { useId } from 'react';

import { classNames } from '../../lib/classNames';
import { Button } from './Button';
import { Spinner } from './Spinner';

export function Loading({ label = 'Loading' }: { label?: string }) {
  return (
    <div className="loading-state" role="status">
      <Spinner label={label} />
      <span>{label}</span>
    </div>
  );
}

export function Skeleton({
  className,
  ...props
}: HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      {...props}
      aria-hidden="true"
      className={classNames('skeleton', className)}
    />
  );
}

interface StateProps {
  title: string;
  description: string;
  actionLabel?: string;
  onAction?: () => void;
}

function StateMessage({
  actionLabel,
  description,
  onAction,
  title,
  tone,
}: StateProps & { tone: string }) {
  const titleId = useId();

  return (
    <section
      className="state-message"
      data-tone={tone}
      aria-labelledby={titleId}
    >
      <span className="state-mark" aria-hidden="true">
        {tone === 'error' ? '!' : tone === 'success' ? '✓' : '—'}
      </span>
      <h3 id={titleId}>{title}</h3>
      <p>{description}</p>
      {actionLabel && onAction ? (
        <Button
          variant={tone === 'error' ? 'danger' : 'secondary'}
          onClick={onAction}
        >
          {actionLabel}
        </Button>
      ) : null}
    </section>
  );
}

export function EmptyState(props: StateProps) {
  return <StateMessage {...props} tone="empty" />;
}

export function ErrorState(props: StateProps) {
  return <StateMessage {...props} tone="error" />;
}

export function SuccessState(props: StateProps) {
  return <StateMessage {...props} tone="success" />;
}

export function VisuallyHidden({ children }: { children: ReactNode }) {
  return <span className="sr-only">{children}</span>;
}
