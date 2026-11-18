import { useState } from 'react';
import { useNavigate } from 'react-router-dom';

import { Button } from '@/shared/ui/Button';
import { Card } from '@/shared/ui/Card';
import { toSearchParams, type SearchValues } from './searchQuery';

type Intent = { city: string | null; checkIn: string | null; checkOut: string | null; rooms: number | null; guestsPerRoom: number | null; amenities: string[]; budgetInr: number | null; budgetScope: string | null };
type Interpretation = { intent: Intent; missingFields: string[]; unsupportedFilters: string[]; readyForBasicSearch: boolean };

/** Public, rate-limited assistant. It translates a request; actual inventory stays in the normal hotel-search endpoint. */
export function AiSearchBox() {
  const [message, setMessage] = useState('');
  const [result, setResult] = useState<Interpretation | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  async function interpret() {
    setLoading(true); setError(null); setResult(null);
    try {
      const response = await fetch('/api/v1/ai/hotel-search/interpret', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ message }) });
      const body = await response.json();
      if (!response.ok) throw new Error(body?.error?.message ?? 'AI search is unavailable.');
      setResult(body.data);
    } catch (caught) { setError(caught instanceof Error ? caught.message : 'AI search is unavailable.'); }
    finally { setLoading(false); }
  }

  function search() {
    if (!result?.readyForBasicSearch) return;
    const values: SearchValues = { city: result.intent.city!, startDate: result.intent.checkIn!, endDate: result.intent.checkOut!, rooms: result.intent.rooms! };
    void navigate({ pathname: '/search', search: toSearchParams(values).toString() });
  }

  return <Card className="mt-6 rounded-2xl border border-brand/20 bg-brand/5 p-5 shadow-sm">
    <h2 className="text-lg font-semibold text-ink">Describe your stay</h2>
    <p className="mt-1 text-sm text-muted">Try: “2 rooms in Pune for 2 guests each, 12–14 December, breakfast and spa under ₹12,000.”</p>
    <label className="sr-only" htmlFor="ai-search-message">Describe your hotel search</label>
    <textarea id="ai-search-message" value={message} onChange={(event) => setMessage(event.target.value)} maxLength={800} rows={3} className="mt-4 w-full rounded-xl border border-line bg-white p-3 text-ink" placeholder="Where and when would you like to stay?" />
    <div className="mt-3 flex flex-wrap gap-3"><Button type="button" disabled={loading || !message.trim()} onClick={() => void interpret()}>{loading ? 'Finding details…' : 'Use AI search'}</Button>{result?.readyForBasicSearch && <Button type="button" variant="secondary" onClick={search}>Search available hotels</Button>}</div>
    {error && <p role="alert" className="mt-3 text-sm text-danger">{error}</p>}
    {result && <div className="mt-4 rounded-xl bg-white p-3 text-sm text-ink"><p className="font-medium">I understood: {result.intent.city ?? 'city needed'} · {result.intent.checkIn ?? 'check-in needed'} to {result.intent.checkOut ?? 'check-out needed'} · {result.intent.rooms ?? 'rooms needed'} room(s).</p>{result.missingFields.length > 0 && <p className="mt-2 text-muted">Please clarify: {result.missingFields.join(', ')}.</p>}{result.unsupportedFilters.length > 0 && <p className="mt-2 text-muted">Saved for the next filtering update: {result.unsupportedFilters.join(', ')}.</p>}</div>}
  </Card>;
}
