import { newCompareCondition, newGroupCondition } from './definitionMapping.js';
import { OperandEditor } from './OperandEditor.jsx';

const GROUP_HELP = {
  all: 'Every condition must be true',
  any: 'At least one condition must be true',
};

const TYPE_LABELS = { compare: 'Compare', all: 'ALL', any: 'ANY' };

function changeConditionType(node, newType) {
  if (newType === node.type) return node;
  if (newType === 'compare') return newCompareCondition();
  if (node.type === 'compare') {
    const group = newGroupCondition(newType);
    return { ...group, conditions: [node] };
  }
  return { ...node, type: newType };
}

function TypeToggle({ value, onChange, label }) {
  return (
    <div role="group" aria-label={label} className="inline-flex overflow-hidden rounded border border-border">
      {['compare', 'all', 'any'].map((type) => (
        <button
          key={type}
          type="button"
          aria-pressed={value === type}
          onClick={() => onChange(type)}
          className={`px-2 py-1 text-xs font-semibold transition-colors duration-150 ${
            value === type ? 'bg-ink text-white' : 'bg-surface text-ink-secondary hover:bg-surface-hover'
          }`}
        >
          {TYPE_LABELS[type]}
        </button>
      ))}
    </div>
  );
}

function RemoveButton({ onClick, label }) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={label}
      className="rounded px-1.5 py-1 text-sm font-semibold text-ink-muted hover:bg-danger-bg hover:text-danger focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-danger"
    >
      ×
    </button>
  );
}

function OperatorSelect({ id, value, onChange }) {
  return (
    <>
      <label htmlFor={id} className="sr-only">
        Operator
      </label>
      <select
        id={id}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className="rounded-md border border-border bg-surface px-2 py-1.5 text-sm font-semibold text-ink focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/30"
      >
        <option value="GT">&gt;</option>
        <option value="LT">&lt;</option>
      </select>
    </>
  );
}

export function ConditionEditor({ condition, onChange, onRemove, depth = 0 }) {
  function setType(newType) {
    onChange(changeConditionType(condition, newType));
  }

  if (condition.type === 'compare') {
    return (
      <div className="rounded-md border border-border bg-surface p-3">
        <div className="flex items-center justify-between gap-2">
          <TypeToggle value="compare" onChange={setType} label="Condition type" />
          {onRemove ? <RemoveButton onClick={onRemove} label="Remove condition" /> : null}
        </div>
        <div className="mt-2.5 flex flex-wrap items-center gap-2">
          <OperandEditor
            operand={condition.left}
            label="Left operand"
            idPrefix={`${condition.id}-left`}
            onChange={(operand) => onChange({ ...condition, left: operand })}
          />
          <OperatorSelect
            id={`${condition.id}-operator`}
            value={condition.operator}
            onChange={(operator) => onChange({ ...condition, operator })}
          />
          <OperandEditor
            operand={condition.right}
            label="Right operand"
            idPrefix={`${condition.id}-right`}
            onChange={(operand) => onChange({ ...condition, right: operand })}
          />
        </div>
      </div>
    );
  }

  function updateChild(index, nextChild) {
    const conditions = condition.conditions.slice();
    conditions[index] = nextChild;
    onChange({ ...condition, conditions });
  }

  function removeChild(index) {
    if (condition.conditions.length <= 1) return;
    onChange({ ...condition, conditions: condition.conditions.filter((_, i) => i !== index) });
  }

  function addCondition() {
    onChange({ ...condition, conditions: [...condition.conditions, newCompareCondition()] });
  }

  function addGroup() {
    onChange({ ...condition, conditions: [...condition.conditions, newGroupCondition('all')] });
  }

  return (
    <div
      className={`rounded-md bg-surface p-3 ${
        depth === 0 ? 'border-2 border-ink shadow-[3px_3px_0_0_var(--color-border)]' : 'border border-border'
      }`}
    >
      <div className="flex items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <TypeToggle value={condition.type} onChange={setType} label="Condition type" />
          <span className="text-xs text-ink-muted">{GROUP_HELP[condition.type]}</span>
        </div>
        {onRemove ? <RemoveButton onClick={onRemove} label="Remove group" /> : null}
      </div>

      <div className="mt-3 space-y-2.5 border-l-2 border-border pl-4">
        {condition.conditions.map((child, index) => (
          <ConditionEditor
            key={child.id}
            condition={child}
            depth={depth + 1}
            onChange={(nextChild) => updateChild(index, nextChild)}
            onRemove={condition.conditions.length > 1 ? () => removeChild(index) : undefined}
          />
        ))}
      </div>

      <div className="mt-3 flex gap-4">
        <button type="button" onClick={addCondition} className="text-sm font-medium text-accent hover:underline">
          + Add condition
        </button>
        <button type="button" onClick={addGroup} className="text-sm font-medium text-accent hover:underline">
          + Add group
        </button>
      </div>
    </div>
  );
}
