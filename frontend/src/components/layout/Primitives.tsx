import type { CSSProperties, HTMLAttributes } from 'react';

import { classNames } from '../../lib/classNames';

type ResponsiveSize = 'small' | 'medium' | 'large' | 'wide';
type Space = '1' | '2' | '3' | '4' | '5' | '6' | '8' | '10' | '12' | '16';
type Columns = 1 | 2 | 3 | 4;

export function Container({
  className,
  ...props
}: HTMLAttributes<HTMLDivElement> & { size?: ResponsiveSize }) {
  const { size = 'wide', ...rest } = props;
  return (
    <div
      {...rest}
      className={classNames('container', className)}
      data-size={size}
    />
  );
}

export function Section({ className, ...props }: HTMLAttributes<HTMLElement>) {
  return <section {...props} className={classNames('section', className)} />;
}

export function Stack({
  className,
  gap = '4',
  style,
  ...props
}: HTMLAttributes<HTMLDivElement> & { gap?: Space }) {
  return (
    <div
      {...props}
      className={classNames('stack', className)}
      style={
        { ...style, '--stack-gap': `var(--space-${gap})` } as CSSProperties
      }
    />
  );
}

export function Grid({
  className,
  columns = 3,
  gap = '6',
  style,
  ...props
}: HTMLAttributes<HTMLDivElement> & { columns?: Columns; gap?: Space }) {
  return (
    <div
      {...props}
      className={classNames('grid', className)}
      data-columns={columns}
      style={
        {
          ...style,
          '--grid-columns': columns,
          '--grid-gap': `var(--space-${gap})`,
        } as CSSProperties
      }
    />
  );
}
