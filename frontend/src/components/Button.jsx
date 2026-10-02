import { buttonClasses } from './buttonStyles.js';

export function Button({ variant = 'primary', type = 'button', className = '', ...props }) {
  return <button type={type} className={buttonClasses(variant, className)} {...props} />;
}
