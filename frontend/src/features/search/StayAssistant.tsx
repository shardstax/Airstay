import { useQuery } from '@tanstack/react-query';
import { type SubmitEvent, useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';

import { api } from '@/shared/api/client';
import { formatDate, pluralize } from '@/shared/lib/format';
import { Button } from '@/shared/ui/Button';

import { searchSchema, toSearchParams } from './searchQuery';

interface Intent {
  city: string | null;
  checkIn: string | null;
  checkOut: string | null;
  rooms: number | null;
  guestsPerRoom: number | null;
  amenities: string[];
  budgetInr: number | null;
  budgetScope: string | null;
}

interface Interpretation {
  intent: Intent;
  missingFields: string[];
  unsupportedFilters: string[];
}

interface Turn {
  message: string;
  reply: string;
  result: Interpretation;
}

const storageKey = 'airstay:stay-assistant:v2';
const popoverId = 'stay-assistant';
const maxTurns = 12;

function amenityLabel(token: string): string {
  if (token === 'WIFI') return 'WiFi';
  return token.charAt(0) + token.slice(1).toLowerCase().replaceAll('_', ' ');
}
const introductions = ['Help me find a hotel in Pune', 'Find a stay in Mumbai this weekend'];

function readHistory(): Turn[] {
  try {
    const saved: unknown = JSON.parse(sessionStorage.getItem(storageKey) ?? '[]');
    return Array.isArray(saved) ? (saved as Turn[]).slice(-maxTurns) : [];
  } catch {
    return [];
  }
}

function assistantReply({ intent, missingFields }: Interpretation) {
  const { city, checkIn, checkOut, rooms, guestsPerRoom, amenities, budgetInr, budgetScope } = intent;
  const parts = [
    city,
    checkIn && `check-in ${formatDate(checkIn)}`,
    checkOut && `check-out ${formatDate(checkOut)}`,
    rooms && pluralize(rooms, 'room'),
    guestsPerRoom && `${pluralize(guestsPerRoom, 'guest')} per room`,
    amenities.map(amenityLabel).join(', '),
    budgetInr &&
      `₹${budgetInr.toLocaleString('en-IN')}${
        budgetScope === 'TOTAL_STAY' ? ' for the whole stay' : budgetScope ? ' per room per night' : ''
      }`,
  ].filter(Boolean);
  const recap = parts.length ? parts.join(' · ') + '\n\n' : '';

  if (missingFields.includes('city')) {
    return recap + 'Which city and state would you like to search? Use a destination listed in AirStay.';
  }
  if (missingFields.includes('checkIn') && missingFields.includes('checkOut')) {
    return recap + 'What are your check-in and check-out dates?';
  }
  if (missingFields.includes('checkIn')) return recap + 'What date would you like to check in?';
  if (missingFields.includes('checkOut')) return recap + 'What date would you like to check out?';
  if (missingFields.includes('rooms')) return recap + 'How many rooms do you need?';
  if (missingFields.includes('budgetScope')) {
    return recap + 'Is that budget for the whole stay or per room, per night?';
  }
  return recap + 'Ready to check availability. You can also tell me what to change.';
}

export function StayAssistant() {
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const [history, setHistory] = useState(readHistory);
  const [draft, setDraft] = useState('');
  const [pending, setPending] = useState('');
  const [failure, setFailure] = useState<{ message: string; detail: string }>();
  const transcript = useRef<HTMLDivElement>(null);
  const composer = useRef<HTMLInputElement>(null);
  const last = history.at(-1)?.result;
  const intent = last?.intent;
  const search = searchSchema().safeParse({
    city: intent?.city,
    startDate: intent?.checkIn,
    endDate: intent?.checkOut,
    rooms: intent?.rooms,
    amenities: intent?.amenities.map(amenityLabel),
  });
  const canSearch = search.success && last?.missingFields.length === 0;
  const isBrowsePage = pathname === '/' || pathname === '/search' || /^\/hotels\/[^/]+$/.test(pathname);
  const status = useQuery({
    queryKey: ['ai-search-status'],
    enabled: isBrowsePage,
    queryFn: async () => (await api.GET('/ai/hotel-search/status')).data?.available ?? false,
    staleTime: 15_000,
    refetchInterval: 60_000,
  });

  useEffect(() => {
    try {
      sessionStorage.setItem(storageKey, JSON.stringify(history));
    } catch {
      // The conversation still works in memory when storage is unavailable.
    }
  }, [history]);

  useEffect(() => {
    if (transcript.current) transcript.current.scrollTop = transcript.current.scrollHeight;
  }, [history, pending, failure]);

  async function send(value: string) {
    const message = value.trim();
    if (!message || pending) return;
    setDraft('');
    setFailure(undefined);
    setPending(message);
    try {
      const response = await fetch('/api/v1/ai/hotel-search/interpret', {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({
          message,
          history: history.map(({ message: previous, reply }) => ({ message: previous, reply })),
        }),
      });
      if (response.status === 429) throw new Error('Please wait a minute before sending another message.');
      if (!response.ok)
        throw new Error('The assistant is temporarily unavailable. Try again or use hotel search.');
      const { data: result } = (await response.json()) as { data: Interpretation };
      const reply = assistantReply(result);
      setHistory((previous) => [...previous, { message, reply, result }].slice(-maxTurns));
    } catch (error) {
      setFailure({
        message,
        detail: error instanceof Error ? error.message : 'Could not send your message. Try again.',
      });
    } finally {
      setPending('');
    }
  }

  function submit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    void send(draft);
  }

  if (!isBrowsePage || status.data !== true) return null;

  return (
    <>
      <Button
        popoverTarget={popoverId}
        className="fixed right-4 bottom-4 z-30 h-12 rounded-full px-5 shadow-lg sm:right-6 sm:bottom-6"
      >
        Ask AI
      </Button>
      <section
        id={popoverId}
        popover="auto"
        role="dialog"
        aria-labelledby="stay-assistant-title"
        className="fixed inset-auto right-3 bottom-3 m-0 w-[min(26rem,calc(100%-1.5rem))] overflow-hidden rounded-2xl border border-line bg-white text-ink shadow-2xl open:flex open:h-[min(38rem,calc(100dvh-1.5rem))] open:flex-col sm:right-6 sm:bottom-6"
        onToggle={(event) => {
          if (event.newState === 'open') {
            composer.current?.focus();
            if (transcript.current) transcript.current.scrollTop = transcript.current.scrollHeight;
          }
        }}
      >
        <header className="flex items-center justify-between gap-2 border-b border-line px-4 py-2">
          <h2 id="stay-assistant-title" className="font-semibold">
            Stay assistant
          </h2>
          <div className="flex items-center gap-1">
            <Button
              variant="ghost"
              disabled={Boolean(pending) || (!history.length && !failure && !draft)}
              onClick={() => {
                setHistory([]);
                setFailure(undefined);
                setDraft('');
                composer.current?.focus();
              }}
            >
              New chat
            </Button>
            <Button
              variant="ghost"
              aria-label="Close assistant"
              popoverTarget={popoverId}
              popoverTargetAction="hide"
            >
              <span aria-hidden="true" className="text-xl">
                ×
              </span>
            </Button>
          </div>
        </header>
        <div
          ref={transcript}
          role="log"
          aria-label="Conversation"
          className="min-h-0 flex-1 space-y-3 overflow-y-auto bg-canvas p-4 text-sm"
        >
          <p className="max-w-[90%] rounded-2xl rounded-tl-sm border border-line bg-white px-3 py-2">
            Where are you headed? Tell me your destination, dates or how many rooms you need.
          </p>
          {!history.length &&
            !pending &&
            !failure &&
            introductions.map((example) => (
              <Button
                key={example}
                variant="secondary"
                className="h-auto rounded-full py-2 text-left"
                onClick={() => void send(example)}
              >
                {example}
              </Button>
            ))}
          {history.map(({ message, reply }, index) => (
            <div key={index} className="space-y-3">
              <p className="ml-auto w-fit max-w-[90%] rounded-2xl rounded-tr-sm bg-brand-700 px-3 py-2 break-words text-white">
                <span className="sr-only">You: </span>
                {message}
              </p>
              <p className="w-fit max-w-[90%] rounded-2xl rounded-tl-sm border border-line bg-white px-3 py-2 break-words whitespace-pre-line">
                <span className="sr-only">Assistant: </span>
                {reply}
              </p>
            </div>
          ))}
          {pending && (
            <>
              <p className="ml-auto w-fit max-w-[90%] rounded-2xl bg-brand-700 px-3 py-2 break-words text-white">
                {pending}
              </p>
              <p role="status" className="text-ink-muted">
                Checking your details…
              </p>
            </>
          )}
          {failure && (
            <div className="space-y-2">
              <p role="alert">{failure.detail}</p>
              <Button variant="secondary" onClick={() => void send(failure.message)}>
                Retry message
              </Button>
            </div>
          )}
          {!pending &&
            !failure &&
            last?.missingFields[0] === 'rooms' &&
            last.missingFields.every((field) => field === 'rooms' || field === 'budgetScope') && (
              <div className="flex flex-wrap gap-2">
                {['1 room', '2 rooms'].map((label) => (
                  <Button key={label} variant="secondary" onClick={() => void send(label)}>
                    {label}
                  </Button>
                ))}
              </div>
            )}
          {!pending &&
            !failure &&
            last?.missingFields.length === 1 &&
            last.missingFields[0] === 'budgetScope' && (
              <div className="flex flex-wrap gap-2">
                {['Whole stay', 'Per room, per night'].map((label) => (
                  <Button key={label} variant="secondary" onClick={() => void send(label)}>
                    {label}
                  </Button>
                ))}
              </div>
            )}
          {!pending && !failure && canSearch && (
            <div className="space-y-2">
              {last.unsupportedFilters.some((filter) => filter !== 'amenities') && (
                <p className="text-ink-muted">
                  Check {last.unsupportedFilters.includes('guestsPerRoom') ? 'guest capacity' : ''}
                  {last.unsupportedFilters.includes('guestsPerRoom') &&
                  last.unsupportedFilters.includes('budgetInr')
                    ? ' and '
                    : ''}
                  {last.unsupportedFilters.includes('budgetInr') ? 'budget' : ''} on each hotel’s listing.
                </p>
              )}
              <Button
                popoverTarget={popoverId}
                popoverTargetAction="hide"
                onClick={() => {
                  void navigate({ pathname: '/search', search: toSearchParams(search.data).toString() });
                }}
              >
                See available hotels
              </Button>
            </div>
          )}
        </div>
        <form onSubmit={submit} className="flex gap-2 border-t border-line p-3">
          <input
            ref={composer}
            aria-label="Message the stay assistant"
            value={draft}
            maxLength={800}
            onChange={(event) => {
              setDraft(event.target.value);
            }}
            placeholder="Type a message…"
            autoComplete="off"
            className="h-10 min-w-0 flex-1 rounded-lg border border-line px-3 text-base sm:text-sm"
          />
          <Button type="submit" disabled={Boolean(pending) || !draft.trim()}>
            Send
          </Button>
        </form>
      </section>
    </>
  );
}
