/** A form-level error that screen readers announce as soon as it appears. */
export function FormError({ message }: { message: string }) {
  return (
    <p role="alert" className="rounded-lg bg-danger-bg px-3 py-2 text-sm text-danger">
      {message}
    </p>
  );
}
