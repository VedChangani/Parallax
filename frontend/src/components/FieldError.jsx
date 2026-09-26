/**
 * @param {object} props
 * @param {string} [props.message]
 * @param {string} [props.id] - pair with the field's `aria-describedby`
 */
export function FieldError({ message, id }) {
  if (!message) return null;
  return (
    <p id={id} role="alert" className="mt-1 text-sm text-danger">
      {message}
    </p>
  );
}
