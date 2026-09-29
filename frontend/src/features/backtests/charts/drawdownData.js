export function drawdownChartData(points) {
  const dates = [];
  const xs = [];
  const ys = [];
  let deepestIndex = -1;

  for (const point of points) {
    if (typeof point.drawdown !== 'number' || !Number.isFinite(point.drawdown)) continue;
    const y = point.drawdown === 0 ? 0 : -point.drawdown * 100;
    if (deepestIndex === -1 || y < ys[deepestIndex]) deepestIndex = ys.length;
    dates.push(point.date);
    xs.push(Date.parse(`${point.date}T00:00:00Z`) / 1000);
    ys.push(y);
  }

  return { dates, xs, ys, deepestIndex };
}
