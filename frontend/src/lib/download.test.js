import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { downloadTextFile } from './download.js';

describe('downloadTextFile', () => {
  let createObjectURL;
  let revokeObjectURL;
  let clicked;

  beforeEach(() => {
    createObjectURL = vi.fn(() => 'blob:parallax-test');
    revokeObjectURL = vi.fn();
    URL.createObjectURL = createObjectURL;
    URL.revokeObjectURL = revokeObjectURL;
    clicked = [];
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function click() {
      clicked.push({ href: this.href, download: this.download, attached: document.body.contains(this) });
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('downloads the exact text under the given filename through one anchor click', async () => {
    const text = 'date,equity\r\n2024-01-02,10000\r\n';

    downloadTextFile('backtest-7-equity-curve.csv', text);

    expect(clicked).toEqual([{ href: 'blob:parallax-test', download: 'backtest-7-equity-curve.csv', attached: true }]);
    const blob = createObjectURL.mock.calls[0][0];
    expect(blob.type).toBe('text/csv;charset=utf-8');
    expect(await blob.text()).toBe(text);
  });

  it('removes the temporary anchor and revokes the object URL', () => {
    downloadTextFile('a.csv', 'x');

    expect(document.querySelector('a[download]')).toBeNull();
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:parallax-test');
  });

  it('still revokes the object URL if the click throws', () => {
    HTMLAnchorElement.prototype.click.mockImplementation(() => {
      throw new Error('blocked');
    });

    expect(() => downloadTextFile('a.csv', 'x')).toThrow('blocked');
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:parallax-test');
  });
});
