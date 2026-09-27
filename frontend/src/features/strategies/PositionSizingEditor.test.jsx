import { fireEvent, render, screen } from '@testing-library/react';
import { useState } from 'react';
import { describe, expect, it } from 'vitest';
import { newCashFractionPositionSizing } from './definitionMapping.js';
import { PositionSizingEditor } from './PositionSizingEditor.jsx';

function Harness({ initial }) {
  const [sizing, setSizing] = useState(initial);
  return (
    <>
      <PositionSizingEditor sizing={sizing} onChange={setSizing} />
      <output data-testid="fraction">{sizing.fraction}</output>
    </>
  );
}

describe('PositionSizingEditor', () => {
  it('displays fraction "1" as 100%', () => {
    render(<Harness initial={newCashFractionPositionSizing('1')} />);
    expect(screen.getByLabelText('Use')).toHaveProperty('value', '100');
  });

  it('displays fraction "0.5" as 50%', () => {
    render(<Harness initial={newCashFractionPositionSizing('0.5')} />);
    expect(screen.getByLabelText('Use')).toHaveProperty('value', '50');
  });

  it('typing 100% stores transport fraction "1" - never a numeric JSON value', () => {
    render(<Harness initial={newCashFractionPositionSizing('0.5')} />);
    fireEvent.change(screen.getByLabelText('Use'), { target: { value: '100' } });
    expect(screen.getByTestId('fraction').textContent).toBe('1');
  });

  it('typing 50% stores transport fraction "0.5"', () => {
    render(<Harness initial={newCashFractionPositionSizing('1')} />);
    fireEvent.change(screen.getByLabelText('Use'), { target: { value: '50' } });
    expect(screen.getByTestId('fraction').textContent).toBe('0.5');
  });

  it('shows a validation error for a malformed percentage without ever calling Number()', () => {
    render(<Harness initial={newCashFractionPositionSizing('abc')} />);
    expect(screen.getByRole('alert')).toBeTruthy();
    expect(screen.getByTestId('fraction').textContent).toBe('abc');
  });

  it('associates the error message with the input via aria-describedby', () => {
    render(<Harness initial={newCashFractionPositionSizing('abc')} />);
    const input = screen.getByLabelText('Use');
    expect(input.getAttribute('aria-invalid')).toBe('true');
    expect(input.getAttribute('aria-describedby')).toBe('position-sizing-error');
  });
});
