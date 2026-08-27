import type { ButtonHTMLAttributes } from 'react';
import { forwardRef } from 'react';

import { classNames } from '../../lib/classNames';
import { Spinner } from './Spinner';

export type ButtonVariant =
  'primary' | 'secondary' | 'outline' | 'ghost' | 'danger';
export type ButtonSize = 'small' | 'medium' | 'large';

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  loading?: boolean;
  loadingLabel?: string;
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  function Button(
    {
      children,
      className,
      disabled,
      loading = false,
      loadingLabel = 'Loading',
      size = 'medium',
      type = 'button',
      variant = 'primary',
      ...props
    },
    ref,
  ) {
    return (
      <button
        {...props}
        ref={ref}
        type={type}
        className={classNames('button', className)}
        data-size={size}
        data-variant={variant}
        disabled={disabled || loading}
        aria-busy={loading || undefined}
      >
        {loading ? <Spinner label={loadingLabel} size="small" /> : null}
        <span>{loading ? loadingLabel : children}</span>
      </button>
    );
  },
);
