import { Badge } from '../../components/Badge.jsx';
import { describeConditionOneLine } from './definitionText.js';

const GROUP_LABELS = { all: 'ALL', any: 'ANY' };

/**
 * A read-only, human-readable rendering of a condition tree - used both by
 * the immutable version detail page and as the Strategy Builder's live
 * preview (the same presentation logic backs both, since a preview is just
 * this same tree rendered from in-progress builder state). Never evaluates
 * anything; purely presentational.
 *
 * @param {object} props
 * @param {object} props.condition - a ConditionDto, or an equally-shaped builder node
 */
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
