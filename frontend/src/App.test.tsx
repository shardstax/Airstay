import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { App } from './App';

function renderAt(path: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <App />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

afterEach(() => vi.restoreAllMocks());

describe('App shell', () => {
  it('shows Connected when the API health endpoint answers UP', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({ status: 'UP' }), { status: 200 }));
    renderAt('/');
    expect(await screen.findByText('Connected')).toBeInTheDocument();
    expect(globalThis.fetch).toHaveBeenCalledWith('/api/v1/actuator/health');
  });

  it('shows Unreachable when the API cannot be reached', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('network down'));
    renderAt('/');
    expect(await screen.findByText('Unreachable')).toBeInTheDocument();
  });

  it('shows Unreachable when the API answers but is not UP', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({ status: 'DOWN' }), { status: 503 }));
    renderAt('/');
    expect(await screen.findByText('Unreachable')).toBeInTheDocument();
  });

  it('has a skip link and a main landmark', () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('{"status":"UP"}'));
    renderAt('/');
    expect(screen.getByRole('link', { name: /skip to content/i })).toHaveAttribute('href', '#main');
    expect(screen.getByRole('main')).toBeInTheDocument();
  });

  it('shows a not-found page for an unknown route', () => {
    renderAt('/nope');
    expect(screen.getByRole('heading', { name: /page not found/i })).toBeInTheDocument();
  });
});
