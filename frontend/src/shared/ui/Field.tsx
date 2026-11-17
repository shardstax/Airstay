import { type ReactNode, useId } from 'react';

import { cn } from '@/shared/lib/cn';

interface ControlProps {
  id: string;
  'aria-invalid': true | undefined;
  'aria-describedby': string | undefined;
  className: string;
}

interface FieldProps {
  label: string;
  error?: string;
  hint?: string;
  id?: string;
  className?: string;
  /** Receives the props that tie the control to its label, hint and error, and spreads them onto the control. */
  children: (control: ControlProps) => ReactNode;
}

/** The label, hint and error shared by every form control, wired to the control for screen readers. */
export function Field({ label, error, hint, id, className, children }: FieldProps) {
  const generated = useId();
  const controlId = id ?? generated;
  const describedBy = error ? `${controlId}-error` : hint ? `${controlId}-hint` : undefined;

  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={controlId} className="text-sm font-medium text-ink">
        {label}
      </label>
      {children({
        id: controlId,
        'aria-invalid': error ? true : undefined,
        'aria-describedby': describedBy,
        className: cn(
          'h-10 rounded-lg border bg-white px-3 text-sm text-ink placeholder:text-ink-muted',
          error ? 'border-danger' : 'border-line hover:border-ink-muted',
          className,
        ),
      })}
      {error ? (
        <p id={`${controlId}-error`} role="alert" className="text-sm text-danger">
          {error}
        </p>
      ) : hint ? (
        <p id={`${controlId}-hint`} className="text-sm text-ink-muted">
          {hint}
        </p>
      ) : null}
    </div>
  );
}
