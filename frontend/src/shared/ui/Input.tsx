import type { InputHTMLAttributes } from 'react';

import { Field } from './Field';

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  error?: string;
  hint?: string;
}

/** A labelled text field (also used for dates). */
export function Input({ label, error, hint, className, id, ...rest }: InputProps) {
  return (
    <Field label={label} error={error} hint={hint} id={id} className={className}>
      {(control) => <input {...control} {...rest} />}
    </Field>
  );
}
