import { Component, type ErrorInfo, type ReactNode } from 'react';

import { EmptyState } from './EmptyState';

interface ErrorBoundaryProps {
  children: ReactNode;
}

/**
 * The last safety net: if a screen throws while rendering, the visitor sees a plain message and a way back instead of a
 * blank page. (React only offers this as a class component.)
 */
export class ErrorBoundary extends Component<ErrorBoundaryProps, { failed: boolean }> {
  state = { failed: false };

  static getDerivedStateFromError() {
    return { failed: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('A screen failed to render', error, info.componentStack);
  }

  render() {
    if (this.state.failed) {
      return (
        <EmptyState
          title="Something went wrong"
          description="Reload the page to try again. If it keeps happening, come back a little later."
        >
          <a href="/" className="text-brand-700 underline underline-offset-4">
            Back to the start
          </a>
        </EmptyState>
      );
    }
    return this.props.children;
  }
}
