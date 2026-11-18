import { useId, useState } from 'react';

import { api } from '@/shared/api/client';
import { describeRequestError, toRequestError } from '@/shared/api/errors';
import { isHttpsUrl } from '@/shared/lib/url';
import { Button } from '@/shared/ui/Button';
import { Input } from '@/shared/ui/Input';

interface UrlListFieldProps {
  label: string;
  value: readonly string[];
  onChange: (next: string[]) => void;
  onUploadState: (uploading: boolean) => void;
  max: number;
  error?: string;
}

/** A list of https links (photos) to add and remove. Each link is checked before it joins the list. */
export function UrlListField({ label, value, onChange, onUploadState, max, error }: UrlListFieldProps) {
  const headingId = useId();
  const [draft, setDraft] = useState('');
  const [problem, setProblem] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);

  const upload = async (file: File) => {
    if (file.size > 2 * 1024 * 1024) {
      setProblem('Choose a JPEG or PNG up to 2 MB.');
      return;
    }
    setUploading(true);
    onUploadState(true);
    setProblem(null);
    try {
      const {
        data,
        error: responseError,
        response,
      } = await api.POST('/admin/photos', {
        body: { file: file.name },
        bodySerializer: () => {
          const form = new FormData();
          form.append('file', file);
          return form;
        },
      });
      if (!data?.url) {
        throw toRequestError(response, responseError);
      }
      onChange([...value, data.url]);
    } catch (cause) {
      setProblem(describeRequestError(cause));
    } finally {
      setUploading(false);
      onUploadState(false);
    }
  };

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
              <img src={link} alt="" className="size-14 shrink-0 rounded-md bg-canvas object-cover" />
              <span className="min-w-0 flex-1 truncate text-sm text-ink">{link}</span>
              <Button
                variant="ghost"
                disabled={uploading}
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
        <fieldset disabled={uploading} className="grid gap-3">
          <Input
            label="Upload photo"
            type="file"
            accept="image/jpeg,image/png"
            hint="JPEG or PNG, up to 2 MB."
            onChange={(event) => {
              const file = event.currentTarget.files?.[0];
              event.currentTarget.value = '';
              if (file) void upload(file);
            }}
          />
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
        </fieldset>
      ) : null}
      {uploading ? <p role="status">Uploading photo…</p> : null}

      {error ? (
        <p role="alert" className="text-sm text-danger">
          {error}
        </p>
      ) : null}
    </div>
  );
}
