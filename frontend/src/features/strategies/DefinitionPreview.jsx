import { ConditionView } from './ConditionView.jsx';
import { describePositionSizing } from './definitionText.js';

export function DefinitionPreview({ definition }) {
  return (
    <div className="space-y-5">
      <div>
        <h3 className="text-xs font-semibold uppercase tracking-wide text-ink-muted">Entry</h3>
        <div className="mt-1.5">
          <ConditionView condition={definition.entryCondition} />
        </div>
      </div>
      <div>
        <h3 className="text-xs font-semibold uppercase tracking-wide text-ink-muted">Exit</h3>
        <div className="mt-1.5">
          <ConditionView condition={definition.exitCondition} />
        </div>
      </div>
      <div>
        <h3 className="text-xs font-semibold uppercase tracking-wide text-ink-muted">Position sizing</h3>
        <p className="mt-1.5 text-sm text-ink">{describePositionSizing(definition.positionSizing)}</p>
      </div>
    </div>
  );
}
