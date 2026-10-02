import { fireEvent, render, screen } from '@testing-library/react';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { ConditionEditor } from './ConditionEditor.jsx';
import { newCompareCondition } from './definitionMapping.js';

function Harness({ initial, onRemove }) {
  const [condition, setCondition] = useState(initial);
  return <ConditionEditor condition={condition} onChange={setCondition} onRemove={onRemove} />;
}

describe('ConditionEditor', () => {
  it('renders a default Compare row as SMA(20) > SMA(50)', () => {
    render(<Harness initial={newCompareCondition()} />);

    expect(screen.getByLabelText('Left operand indicator')).toHaveProperty('value', 'SMA');
    expect(screen.getByLabelText('Left operand period')).toHaveProperty('value', '20');
    expect(screen.getByLabelText('Right operand indicator')).toHaveProperty('value', 'SMA');
    expect(screen.getByLabelText('Right operand period')).toHaveProperty('value', '50');
  });

  it('never shows a remove button at the root (the entry/exit condition can only be edited)', () => {
    render(<Harness initial={newCompareCondition()} />);
    expect(screen.queryByRole('button', { name: /remove/i })).toBeNull();
  });

  it('changes the operator between > and <', () => {
    render(<Harness initial={newCompareCondition()} />);
    fireEvent.change(screen.getByLabelText('Operator'), { target: { value: 'LT' } });
    expect(screen.getByLabelText('Operator')).toHaveProperty('value', 'LT');
  });

  it('switches the left operand to Constant and edits its exact text', () => {
    render(<Harness initial={newCompareCondition()} />);
    fireEvent.change(screen.getByLabelText('Left operand type'), { target: { value: 'constant' } });
    fireEvent.change(screen.getByLabelText('Left operand value'), { target: { value: '70.50' } });
    expect(screen.getByLabelText('Left operand value')).toHaveProperty('value', '70.50');
  });

  it('switches an operand to Close with no extra fields', () => {
    render(<Harness initial={newCompareCondition()} />);
    fireEvent.change(screen.getByLabelText('Left operand type'), { target: { value: 'close' } });
    expect(screen.queryByLabelText('Left operand indicator')).toBeNull();
    expect(screen.queryByLabelText('Left operand period')).toBeNull();
    expect(screen.queryByLabelText('Left operand value')).toBeNull();
  });

  it('switching root type to ALL wraps the existing Compare rather than discarding it', () => {
    render(<Harness initial={newCompareCondition()} />);
    fireEvent.click(screen.getByRole('button', { name: 'ALL' }));

    expect(screen.getByText('Every condition must be true')).toBeTruthy();
    expect(screen.getByLabelText('Left operand indicator')).toHaveProperty('value', 'SMA');
    expect(screen.getByLabelText('Left operand period')).toHaveProperty('value', '20');
  });

  it('switching between ALL and ANY keeps the same children', () => {
    render(<Harness initial={newCompareCondition()} />);
    fireEvent.click(screen.getByRole('button', { name: 'ALL' }));
    fireEvent.click(screen.getByRole('button', { name: '+ Add condition' }));
    expect(screen.getAllByLabelText('Left operand type')).toHaveLength(2);

    fireEvent.click(screen.getAllByRole('button', { name: 'ANY' })[0]);
    expect(screen.getByText('At least one condition must be true')).toBeTruthy();
    expect(screen.getAllByLabelText('Left operand type')).toHaveLength(2);
  });

  it('adds and removes a condition inside a group, never leaving it empty', () => {
    render(<Harness initial={newCompareCondition()} />);
    fireEvent.click(screen.getByRole('button', { name: 'ALL' }));

    fireEvent.click(screen.getByRole('button', { name: '+ Add condition' }));
    expect(screen.getAllByLabelText('Left operand type')).toHaveLength(2);
    expect(screen.getAllByRole('button', { name: 'Remove condition' })).toHaveLength(2);

    fireEvent.click(screen.getAllByRole('button', { name: 'Remove condition' })[0]);
    expect(screen.getAllByLabelText('Left operand type')).toHaveLength(1);
    expect(screen.queryByRole('button', { name: 'Remove condition' })).toBeNull();
  });

  it('adds a nested group, which is itself removable (unlike the root)', () => {
    render(<Harness initial={newCompareCondition()} />);
    fireEvent.click(screen.getByRole('button', { name: 'ALL' }));
    fireEvent.click(screen.getByRole('button', { name: '+ Add group' }));

    expect(screen.getAllByText('Every condition must be true')).toHaveLength(2);
    expect(screen.getByRole('button', { name: 'Remove group' })).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Remove group' }));
    expect(screen.getAllByText('Every condition must be true')).toHaveLength(1);
  });

  it('calls onRemove for a removable non-root condition', () => {
    const onRemove = vi.fn();
    const condition = newCompareCondition();
    render(<Harness initial={condition} onRemove={onRemove} />);

    fireEvent.click(screen.getByRole('button', { name: 'Remove condition' }));
    expect(onRemove).toHaveBeenCalledTimes(1);
  });

  it('edits each Compare row independently when there are multiple', () => {
    render(<Harness initial={newCompareCondition()} />);
    fireEvent.click(screen.getByRole('button', { name: 'ALL' }));
    fireEvent.click(screen.getByRole('button', { name: '+ Add condition' }));

    const periods = screen.getAllByLabelText('Left operand period');
    expect(periods).toHaveLength(2);
    fireEvent.change(periods[1], { target: { value: '9' } });

    const updated = screen.getAllByLabelText('Left operand period');
    expect(updated[0]).toHaveProperty('value', '20');
    expect(updated[1]).toHaveProperty('value', '9');
  });
});
