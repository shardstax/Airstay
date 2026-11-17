import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { Button } from './Button';
import { Input } from './Input';

describe('Input', () => {
  it('connects the label to the field', () => {
    render(<Input label="Email" />);
    expect(screen.getByLabelText('Email')).toBeInTheDocument();
  });

  it('announces an error and links it to the field', () => {
    render(<Input label="Password" error="Too short" />);
    const field = screen.getByLabelText('Password');
    expect(field).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByRole('alert')).toHaveTextContent('Too short');
    expect(field).toHaveAccessibleDescription('Too short');
  });

  it('links a hint when there is no error', () => {
    render(<Input label="Email" hint="We never share it." />);
    expect(screen.getByLabelText('Email')).toHaveAccessibleDescription('We never share it.');
  });
});

describe('Button', () => {
  it('does not click while loading and says it is busy', async () => {
    const onClick = vi.fn();
    render(
      <Button loading onClick={onClick}>
        Pay
      </Button>,
    );
    const button = screen.getByRole('button', { name: 'Pay' });
    expect(button).toBeDisabled();
    expect(button).toHaveAttribute('aria-busy', 'true');
    await userEvent.click(button);
    expect(onClick).not.toHaveBeenCalled();
  });

  it('defaults to type=button so it never submits a form by accident', () => {
    render(<Button>Go</Button>);
    expect(screen.getByRole('button')).toHaveAttribute('type', 'button');
  });
});
