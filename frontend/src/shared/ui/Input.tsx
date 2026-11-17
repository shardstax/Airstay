import { type InputHTMLAttributes, useId } from 'react';

import { cn } from '@/shared/lib/cn';

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  error?: string;
  hint?: string;
}

/** A labelled text field. The label, hint and error are wired to the input for screen readers. */
export function Input({ label, error, hint, className, id, ...rest }: InputProps) {
  const generated = useId();
  const inputId = id ?? generated;
  const describedBy = error ? `${inputId}-error` : hint ? `${inputId}-hint` : undefined;
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={inputId} className="text-sm font-medium text-ink">
        {label}
      </label>
      <input
        id={inputId}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        className={cn(
          'h-10 rounded-lg border bg-white px-3 text-sm text-ink placeholder:text-ink-muted',
          error ? 'border-danger' : 'border-line hover:border-ink-muted',
          className,
        )}
        {...rest}
      />
      {error ? (
        <p id={`${inputId}-error`} role="alert" className="text-sm text-danger">
          {error}
        </p>
      ) : hint ? (
        <p id={`${inputId}-hint`} className="text-sm text-ink-muted">
          {hint}
        </p>
      ) : null}
    </div>
  );
}
