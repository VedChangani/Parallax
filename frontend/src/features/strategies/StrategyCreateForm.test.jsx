import { fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { StrategyCreateForm } from './StrategyCreateForm.jsx';

function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });
}

function entrySection() {
  return screen.getByText('Entry condition').closest('section');
}

function fillMetadata({ name, description }) {
  fireEvent.change(screen.getByLabelText('Name'), { target: { value: name } });
  if (description !== undefined) {
    fireEvent.change(screen.getByLabelText('Description'), { target: { value: description } });
  }
}

function submit() {
  fireEvent.click(screen.getByRole('button', { name: /create strategy/i }));
}

describe('StrategyCreateForm', () => {
  beforeEach(() => {
    globalThis.fetch = vi.fn();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('requires a name before submitting', () => {
    const onCreated = vi.fn();
    render(<StrategyCreateForm onCreated={onCreated} />);

    submit();

    expect(screen.getByText('Name is required.')).toBeTruthy();
    expect(globalThis.fetch).not.toHaveBeenCalled();
    expect(onCreated).not.toHaveBeenCalled();
  });

  it('builds SMA(20) > SMA(50) AND RSI(14) < 70 and posts the exact DTO tree', async () => {
    const created = { id: 9, name: 'Momentum Cross', description: 'SMA trend', latestVersionNumber: 1, createdAt: '2024-01-01T00:00:00Z' };
    globalThis.fetch.mockResolvedValue(jsonResponse(created, 201));
    const onCreated = vi.fn();

    render(<StrategyCreateForm onCreated={onCreated} />);
    fillMetadata({ name: 'Momentum Cross', description: 'SMA trend' });

    // Entry: convert the default Compare (SMA(20) > SMA(50)) to an ALL group, then add RSI(14) < 70.
    fireEvent.click(within(entrySection()).getByRole('button', { name: 'ALL' }));
    fireEvent.click(within(entrySection()).getByRole('button', { name: '+ Add condition' }));

    const leftTypes = within(entrySection()).getAllByLabelText('Left operand type');
    fireEvent.change(leftTypes[1], { target: { value: 'indicator' } });
    const leftIndicators = within(entrySection()).getAllByLabelText('Left operand indicator');
    fireEvent.change(leftIndicators[1], { target: { value: 'RSI' } });
    const leftPeriods = within(entrySection()).getAllByLabelText('Left operand period');
    fireEvent.change(leftPeriods[1], { target: { value: '14' } });

    const operators = within(entrySection()).getAllByLabelText('Operator');
    fireEvent.change(operators[1], { target: { value: 'LT' } });

    const rightTypes = within(entrySection()).getAllByLabelText('Right operand type');
    fireEvent.change(rightTypes[1], { target: { value: 'constant' } });
    const rightValues = within(entrySection()).getAllByLabelText('Right operand value');
    fireEvent.change(rightValues[0], { target: { value: '70' } });

    submit();

    await vi.waitFor(() => expect(onCreated).toHaveBeenCalledWith(created));

    const [path, init] = globalThis.fetch.mock.calls[0];
    expect(path).toBe('/api/strategies');
    const body = JSON.parse(init.body);
    expect(body.name).toBe('Momentum Cross');
    expect(body.description).toBe('SMA trend');
    expect(body.definition.entryCondition).toEqual({
      type: 'all',
      conditions: [
        { type: 'compare', left: { type: 'indicator', indicator: 'SMA', period: 20 }, operator: 'GT', right: { type: 'indicator', indicator: 'SMA', period: 50 } },
        { type: 'compare', left: { type: 'indicator', indicator: 'RSI', period: 14 }, operator: 'LT', right: { type: 'constant', value: '70' } },
      ],
    });
    expect(body.definition.positionSizing).toEqual({ type: 'cashFraction', fraction: '1' });
  });

  it('offers ATR and ROC, previews them, and posts them in the DTO tree', async () => {
    const created = { id: 10, name: 'Volatility Momentum', description: '', latestVersionNumber: 1, createdAt: '2024-01-01T00:00:00Z' };
    globalThis.fetch.mockResolvedValue(jsonResponse(created, 201));
    const onCreated = vi.fn();

    render(<StrategyCreateForm onCreated={onCreated} />);
    fillMetadata({ name: 'Volatility Momentum' });

    const options = Array.from(within(entrySection()).getByLabelText('Left operand indicator').querySelectorAll('option')).map((o) => o.value);
    expect(options).toEqual(['SMA', 'EMA', 'RSI', 'ATR', 'ROC']);

    // Entry: ATR(14) > 2 (left indicator ATR, right operand a constant).
    fireEvent.change(within(entrySection()).getByLabelText('Left operand indicator'), { target: { value: 'ATR' } });
    fireEvent.change(within(entrySection()).getByLabelText('Left operand period'), { target: { value: '14' } });
    fireEvent.change(within(entrySection()).getByLabelText('Right operand type'), { target: { value: 'constant' } });
    fireEvent.change(within(entrySection()).getByLabelText('Right operand value'), { target: { value: '2' } });

    // Exit: ROC(1) < -5 - period 1 is valid for ROC (unlike RSI).
    const exitSection = screen.getByText('Exit condition').closest('section');
    fireEvent.change(within(exitSection).getByLabelText('Left operand indicator'), { target: { value: 'ROC' } });
    fireEvent.change(within(exitSection).getByLabelText('Left operand period'), { target: { value: '1' } });
    fireEvent.change(within(exitSection).getByLabelText('Right operand value'), { target: { value: '-5' } });

    // The live preview renders the same text as the saved-version pages.
    expect(screen.getByText('ATR(14) > 2')).toBeTruthy();
    expect(screen.getByText('ROC(1) < -5')).toBeTruthy();

    submit();

    await vi.waitFor(() => expect(onCreated).toHaveBeenCalledWith(created));

    const body = JSON.parse(globalThis.fetch.mock.calls[0][1].body);
    expect(body.definition.entryCondition).toEqual({
      type: 'compare',
      left: { type: 'indicator', indicator: 'ATR', period: 14 },
      operator: 'GT',
      right: { type: 'constant', value: '2' },
    });
    expect(body.definition.exitCondition).toEqual({
      type: 'compare',
      left: { type: 'indicator', indicator: 'ROC', period: 1 },
      operator: 'LT',
      right: { type: 'constant', value: '-5' },
    });
  });

  it('applies the shared period rule to ATR/ROC (min 1) and keeps the RSI minimum of 2', () => {
    render(<StrategyCreateForm onCreated={vi.fn()} />);
    fillMetadata({ name: 'X' });

    fireEvent.change(within(entrySection()).getByLabelText('Left operand indicator'), { target: { value: 'ATR' } });
    fireEvent.change(within(entrySection()).getByLabelText('Left operand period'), { target: { value: '0' } });
    expect(screen.getByText('Period must be at least 1.')).toBeTruthy();

    fireEvent.change(within(entrySection()).getByLabelText('Left operand period'), { target: { value: '1' } });
    expect(screen.queryByText('Period must be at least 1.')).toBeNull();

    fireEvent.change(within(entrySection()).getByLabelText('Left operand indicator'), { target: { value: 'RSI' } });
    expect(screen.getByText('RSI period must be at least 2.')).toBeTruthy();
  });

  it('blocks submission and shows a message when the builder has an invalid field', () => {
    render(<StrategyCreateForm onCreated={vi.fn()} />);
    fillMetadata({ name: 'Momentum Cross' });

    fireEvent.change(within(entrySection()).getByLabelText('Left operand period'), { target: { value: '0' } });
    submit();

    expect(screen.getByText(/fix the highlighted fields/i)).toBeTruthy();
    expect(globalThis.fetch).not.toHaveBeenCalled();
  });

  it('maps a 400 Bean Validation field error onto the offending metadata field', async () => {
    globalThis.fetch.mockResolvedValue(
      jsonResponse({ title: 'Malformed request', errors: [{ field: 'name', message: 'must not be blank' }] }, 400),
    );
    render(<StrategyCreateForm onCreated={vi.fn()} />);
    fillMetadata({ name: 'Momentum Cross' });
    submit();

    expect(await screen.findByText('must not be blank')).toBeTruthy();
  });

  it('shows a 409 conflict as a generic form-level message', async () => {
    globalThis.fetch.mockResolvedValue(jsonResponse({ title: 'Conflict', detail: 'a strategy named "Momentum Cross" already exists' }, 409));
    render(<StrategyCreateForm onCreated={vi.fn()} />);
    fillMetadata({ name: 'Momentum Cross' });
    submit();

    expect(await screen.findByText('a strategy named "Momentum Cross" already exists')).toBeTruthy();
  });

  it('surfaces a 422 semantic definition error as a definition-level banner, not a raw field error', async () => {
    globalThis.fetch.mockResolvedValue(
      jsonResponse({ title: 'Invalid strategy definition', detail: 'RSI period must be >= 2, was 1', field: 'definition.entryCondition.left' }, 422),
    );
    render(<StrategyCreateForm onCreated={vi.fn()} />);
    fillMetadata({ name: 'Momentum Cross' });
    submit();

    expect(await screen.findByText('RSI period must be >= 2, was 1')).toBeTruthy();
  });

  it('shows a network failure as a form-level message', async () => {
    globalThis.fetch.mockRejectedValue(new TypeError('fetch failed'));
    render(<StrategyCreateForm onCreated={vi.fn()} />);
    fillMetadata({ name: 'Momentum Cross' });
    submit();

    expect(await screen.findByText('fetch failed')).toBeTruthy();
  });
});
