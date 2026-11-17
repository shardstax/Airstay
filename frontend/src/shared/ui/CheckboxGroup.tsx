import { useId } from 'react';

interface CheckboxGroupProps {
  legend: string;
  options: readonly string[];
  value: readonly string[];
  onChange: (next: string[]) => void;
  error?: string;
  hint?: string;
}

/** A set of checkboxes for choosing several items from a fixed list. */
export function CheckboxGroup({ legend, options, value, onChange, error, hint }: CheckboxGroupProps) {
  const id = useId();
  const describedBy = error ? `${id}-error` : hint ? `${id}-hint` : undefined;

  return (
    <fieldset aria-describedby={describedBy}>
      <legend className="text-sm font-medium text-ink">{legend}</legend>
      <ul className="mt-2 grid grid-cols-2 gap-2 sm:grid-cols-3">
        {options.map((option) => {
          const checked = value.includes(option);
          return (
            <li key={option}>
              <label className="flex cursor-pointer items-center gap-2 rounded-lg border border-line px-3 py-2 text-sm text-ink has-[:checked]:border-brand-600 has-[:checked]:bg-brand-50">
                <input
                  type="checkbox"
                  className="size-4 accent-brand-600"
                  checked={checked}
                  onChange={(event) => {
                    onChange(
                      event.target.checked ? [...value, option] : value.filter((item) => item !== option),
                    );
                  }}
                />
                {option}
              </label>
            </li>
          );
        })}
      </ul>
      {error ? (
        <p id={`${id}-error`} role="alert" className="mt-1.5 text-sm text-danger">
          {error}
        </p>
      ) : hint ? (
        <p id={`${id}-hint`} className="mt-1.5 text-sm text-ink-muted">
          {hint}
        </p>
      ) : null}
    </fieldset>
  );
}
