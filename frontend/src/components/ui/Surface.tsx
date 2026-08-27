import type { HTMLAttributes, ReactNode } from 'react';

import { classNames } from '../../lib/classNames';

type Tone = 'neutral' | 'accent' | 'success' | 'warning' | 'error' | 'info';

export function Card({ className, ...props }: HTMLAttributes<HTMLElement>) {
  return <article {...props} className={classNames('card', className)} />;
}

export function Panel({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div {...props} className={classNames('panel', className)} />;
}

export function Badge({
  children,
  tone = 'neutral',
}: {
  children: ReactNode;
  tone?: Tone;
}) {
  return (
    <span className="badge" data-tone={tone}>
      {children}
    </span>
  );
}

export function Alert({
  children,
  title,
  tone = 'info',
}: {
  children: ReactNode;
  title: string;
  tone?: Tone;
}) {
  return (
    <div
      className="alert"
      data-tone={tone}
      role={tone === 'error' ? 'alert' : 'status'}
    >
      <span className="alert-mark" aria-hidden="true">
        {tone === 'error' ? '!' : tone === 'success' ? '✓' : 'i'}
      </span>
      <div>
        <strong>{title}</strong>
        <div>{children}</div>
      </div>
    </div>
  );
}
