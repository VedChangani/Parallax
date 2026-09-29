import { Badge } from '../../components/Badge.jsx';
import { describeConditionOneLine } from './definitionText.js';

const GROUP_LABELS = { all: 'ALL', any: 'ANY' };

export function ConditionView({ condition }) {
  if (condition.type === 'compare') {
    return <p className="text-sm text-ink">{describeConditionOneLine(condition)}</p>;
  }

  return (
    <div>
      <Badge>{GROUP_LABELS[condition.type]}</Badge>
      <div className="mt-1.5 space-y-1.5 border-l-2 border-border pl-4">
        {condition.conditions.map((child, index) => (
          <ConditionView key={child.id ?? index} condition={child} />
        ))}
      </div>
    </div>
  );
}
