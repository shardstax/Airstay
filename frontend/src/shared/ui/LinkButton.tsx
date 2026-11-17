import { Link, type LinkProps } from 'react-router-dom';

import { buttonClasses, type ButtonVariant } from './Button';

interface LinkButtonProps extends LinkProps {
  variant?: ButtonVariant;
}

/** A navigation link that looks like a button. Use a real Button for actions; use this for going somewhere. */
export function LinkButton({ variant = 'primary', className, ...rest }: LinkButtonProps) {
  return <Link className={buttonClasses(variant, className)} {...rest} />;
}
