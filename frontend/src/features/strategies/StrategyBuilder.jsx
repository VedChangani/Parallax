import { ConditionEditor } from './ConditionEditor.jsx';
import { DefinitionPreview } from './DefinitionPreview.jsx';
import { PositionSizingEditor } from './PositionSizingEditor.jsx';

/**
 * The visual Strategy Builder (D-31 §6/§20): a structured editor for the
 * exact supported grammar (Compare/All/Any conditions over
 * Indicator/Close/Constant operands, Cash-fraction position sizing) with a
 * live human-readable preview alongside it. No JSON is ever shown as the
 * primary editing surface.
 *
 * @param {object} props
 * @param {{entryCondition: object, exitCondition: object, positionSizing: object}} props.state
 * @param {(next: object) => void} props.onChange
 */
export function StrategyBuilder({ state, onChange }) {
  return (
    <div className="grid gap-6 lg:grid-cols-[1fr_320px]">
      <div className="space-y-6">
        <section>
          <h2 className="mb-2 text-sm font-semibold uppercase tracking-wide text-ink-secondary">Entry condition</h2>
          <ConditionEditor
            condition={state.entryCondition}
            onChange={(entryCondition) => onChange({ ...state, entryCondition })}
          />
        </section>

        <section>
          <h2 className="mb-2 text-sm font-semibold uppercase tracking-wide text-ink-secondary">Exit condition</h2>
          <ConditionEditor
            condition={state.exitCondition}
            onChange={(exitCondition) => onChange({ ...state, exitCondition })}
          />
        </section>

        <section>
          <h2 className="mb-2 text-sm font-semibold uppercase tracking-wide text-ink-secondary">Position sizing</h2>
          <div className="rounded-md border border-border bg-surface p-3">
            <PositionSizingEditor
              sizing={state.positionSizing}
              onChange={(positionSizing) => onChange({ ...state, positionSizing })}
            />
          </div>
        </section>
      </div>

      <aside className="lg:sticky lg:top-6 lg:self-start">
        <div className="rounded-md border border-border bg-page/60 p-4">
          <h2 className="mb-3 text-xs font-semibold uppercase tracking-wide text-ink-muted">Definition preview</h2>
          <DefinitionPreview definition={state} />
        </div>
      </aside>
    </div>
  );
}
