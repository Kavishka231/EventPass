import type {
  InputHTMLAttributes,
  LabelHTMLAttributes,
  SelectHTMLAttributes,
  TextareaHTMLAttributes,
} from 'react';
import { forwardRef, useId } from 'react';

import { classNames } from '../../lib/classNames';

export const Label = forwardRef<
  HTMLLabelElement,
  LabelHTMLAttributes<HTMLLabelElement>
>(function Label({ className, ...props }, ref) {
  return (
    <label
      {...props}
      ref={ref}
      className={classNames('field-label', className)}
    />
  );
});

export function FieldError({
  id,
  children,
}: {
  id?: string;
  children: React.ReactNode;
}) {
  return (
    <p className="field-error" id={id} role="alert">
      <span aria-hidden="true">!</span>
      {children}
    </p>
  );
}

export const Input = forwardRef<
  HTMLInputElement,
  InputHTMLAttributes<HTMLInputElement>
>(function Input({ className, ...props }, ref) {
  return (
    <input
      {...props}
      ref={ref}
      className={classNames('field-control', className)}
    />
  );
});

export const Select = forwardRef<
  HTMLSelectElement,
  SelectHTMLAttributes<HTMLSelectElement>
>(function Select({ children, className, ...props }, ref) {
  return (
    <select
      {...props}
      ref={ref}
      className={classNames('field-control', 'field-select', className)}
    >
      {children}
    </select>
  );
});

export const Textarea = forwardRef<
  HTMLTextAreaElement,
  TextareaHTMLAttributes<HTMLTextAreaElement>
>(function Textarea({ className, ...props }, ref) {
  return (
    <textarea
      {...props}
      ref={ref}
      className={classNames('field-control', 'field-textarea', className)}
    />
  );
});

interface ChoiceProps extends Omit<
  InputHTMLAttributes<HTMLInputElement>,
  'type'
> {
  label: React.ReactNode;
}

function Choice({
  className,
  id,
  label,
  type,
  ...props
}: ChoiceProps & { type: 'checkbox' | 'radio' }) {
  const generatedId = useId();
  const controlId = id ?? generatedId;

  return (
    <label className={classNames('choice', className)} htmlFor={controlId}>
      <input {...props} id={controlId} type={type} />
      <span aria-hidden="true" className="choice-indicator" />
      <span>{label}</span>
    </label>
  );
}

export function Checkbox(props: ChoiceProps) {
  return <Choice {...props} type="checkbox" />;
}

export function Radio(props: ChoiceProps) {
  return <Choice {...props} type="radio" />;
}
