import type { SelectHTMLAttributes } from 'react';

import { Field } from './Field';

interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label: string;
  error?: string;
  hint?: string;
}

/** A labelled drop-down. Pass the options as children. */
export function Select({ label, error, hint, className, id, children, ...rest }: SelectProps) {
  return (
    <Field label={label} error={error} hint={hint} id={id} className={className}>
      {(control) => (
        <select {...control} {...rest}>
          {children}
        </select>
      )}
    </Field>
  );
}
