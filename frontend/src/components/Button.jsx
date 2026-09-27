import { buttonClasses } from './buttonStyles.js';

/**
 * @param {object} props
 * @param {'primary' | 'secondary' | 'subtle' | 'destructive'} [props.variant]
 * @param {'button' | 'submit' | 'reset'} [props.type]
 * @param {string} [props.className]
 */
export function Button({ variant = 'primary', type = 'button', className = '', ...props }) {
  return <button type={type} className={buttonClasses(variant, className)} {...props} />;
}
