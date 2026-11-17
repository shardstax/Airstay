import { useId, useState } from 'react';

import { isHttpsUrl } from '@/shared/lib/url';
import { Button } from '@/shared/ui/Button';
import { Input } from '@/shared/ui/Input';

interface UrlListFieldProps {
  label: string;
  value: readonly string[];
  onChange: (next: string[]) => void;
  max: number;
  error?: string;
}

/** A list of https links (photos) to add and remove. Each link is checked before it joins the list. */
export function UrlListField({ label, value, onChange, max, error }: UrlListFieldProps) {
  const headingId = useId();
  const [draft, setDraft] = useState('');
  const [problem, setProblem] = useState<string | null>(null);

  const add = () => {
    const link = draft.trim();
    if (!isHttpsUrl(link)) {
      setProblem('Enter a full https address, such as https://images.example.com/front.jpg.');
    } else if (value.includes(link)) {
      setProblem('That photo is already in the list.');
    } else {
      onChange([...value, link]);
      setDraft('');
      setProblem(null);
    }
  };

  return (
    <div role="group" aria-labelledby={headingId} className="flex flex-col gap-3">
      <p id={headingId} className="text-sm font-medium text-ink">
        {label}{' '}
        <span className="font-normal text-ink-muted">
          ({value.length} of {max})
        </span>
      </p>

      {value.length > 0 ? (
        <ul className="flex flex-col gap-2">
          {value.map((link) => (
            <li
              key={link}
              className="flex items-center justify-between gap-3 rounded-lg border border-line px-3 py-2"
            >
              <span className="min-w-0 truncate text-sm text-ink">{link}</span>
              <Button
                variant="ghost"
                aria-label={`Remove ${link}`}
                onClick={() => {
                  onChange(value.filter((item) => item !== link));
                }}
              >
                Remove
              </Button>
            </li>
          ))}
        </ul>
      ) : null}

      {value.length < max ? (
        <div className="flex items-start gap-3">
          <div className="flex-1">
            <Input
              label="Photo address"
              type="url"
              placeholder="https://"
              value={draft}
              error={problem ?? undefined}
              onChange={(event) => {
                setDraft(event.target.value);
                setProblem(null);
              }}
              onKeyDown={(event) => {
                if (event.key === 'Enter') {
                  event.preventDefault();
                  add();
                }
              }}
            />
          </div>
          <Button variant="secondary" className="mt-[1.625rem]" onClick={add}>
            Add photo
          </Button>
        </div>
      ) : null}

      {error ? (
        <p role="alert" className="text-sm text-danger">
          {error}
        </p>
      ) : null}
    </div>
  );
}
