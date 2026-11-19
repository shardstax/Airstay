import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { api } from '@/shared/api/client';
import { toRequestError } from '@/shared/api/errors';
import { useSession } from '@/shared/session/session';
import { Button } from '@/shared/ui/Button';
import { Card } from '@/shared/ui/Card';
import { EmptyState } from '@/shared/ui/EmptyState';

const settingsKey = ['ai-search-settings'] as const;

export function AiSettingsPage() {
  const { user } = useSession();
  const queryClient = useQueryClient();
  const isAdmin = user?.roles.includes('ADMIN') === true;
  const settings = useQuery({
    queryKey: [...settingsKey, user?.id],
    enabled: isAdmin,
    queryFn: async () => {
      const { data, error, response } = await api.GET('/admin/ai-search/settings');
      if (!data) throw toRequestError(response, error);
      return data;
    },
  });
  const save = useMutation({
    mutationFn: async (enabled: boolean) => {
      const { data, error, response } = await api.PUT('/admin/ai-search/settings', { body: { enabled } });
      if (!data) throw toRequestError(response, error);
      return data;
    },
    onSuccess: (data) => {
      queryClient.setQueryData([...settingsKey, user?.id], data);
      void queryClient.invalidateQueries({ queryKey: ['ai-search-status'] });
    },
  });

  if (!isAdmin) return <EmptyState title="Site admin access required" />;
  const current = settings.data;

  return (
    <div className="mx-auto max-w-xl space-y-4">
      <h1 className="text-2xl font-semibold text-ink">Site settings</h1>
      <Card>
        <h2 className="text-lg font-semibold text-ink">AI stay assistant</h2>
        <p className="mt-1 text-sm text-ink-muted">Control whether visitors can use the assistant.</p>
        {settings.isPending && (
          <p role="status" className="mt-4">
            Loading settings…
          </p>
        )}
        {settings.isError && (
          <Button
            className="mt-4"
            variant="secondary"
            onClick={() => {
              void settings.refetch();
            }}
          >
            Retry loading settings
          </Button>
        )}
        {current && (
          <div className="mt-4 space-y-3">
            <p role="status">{current.enabled ? 'On' : 'Off'}</p>
            {!current.configured && (
              <p className="text-sm text-ink-muted">
                The AI provider must be enabled and configured on the server.
              </p>
            )}
            <Button
              variant="secondary"
              loading={save.isPending}
              onClick={() => {
                save.mutate(!current.enabled);
              }}
            >
              {current.enabled ? 'Turn off AI assistant' : 'Turn on AI assistant'}
            </Button>
          </div>
        )}
        {save.isError && (
          <p role="alert" className="mt-3 text-danger">
            {save.error.message}
          </p>
        )}
      </Card>
    </div>
  );
}
