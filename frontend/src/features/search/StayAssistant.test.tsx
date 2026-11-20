import { screen, waitFor } from '@testing-library/react';

import { mockApi } from '@/test/mockApi';
import { renderRoute } from '@/test/renderRoute';

import { StayAssistant } from './StayAssistant';

describe('stay assistant availability', () => {
  it('shows the launcher when the server reports availability', async () => {
    mockApi({ 'GET /ai/hotel-search/status': { data: { available: true } } });
    renderRoute(<StayAssistant />);
    expect(await screen.findByRole('button', { name: 'Ask AI' })).toBeInTheDocument();
  });

  it('hides the launcher when the server reports unavailable', async () => {
    mockApi({ 'GET /ai/hotel-search/status': { data: { available: false } } });
    const { queryClient } = renderRoute(<StayAssistant />);
    await waitFor(() => {
      expect(queryClient.getQueryData(['ai-search-status'])).toBe(false);
    });
    expect(screen.queryByRole('button', { name: 'Ask AI' })).not.toBeInTheDocument();
  });
});
