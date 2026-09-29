/**
 * Saves `text` to the user's machine as `filename` by way of a temporary
 * object URL and a synthetic anchor click - the standard browser download
 * with no server round-trip and no new dependency. The text is written
 * exactly as given (UTF-8, no BOM added, no line-ending changes).
 *
 * @param {string} filename
 * @param {string} text
 * @param {string} [mimeType]
 */
export function downloadTextFile(filename, text, mimeType = 'text/csv;charset=utf-8') {
  const url = URL.createObjectURL(new Blob([text], { type: mimeType }));
  try {
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = filename;
    anchor.rel = 'noopener';
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
  } finally {
    URL.revokeObjectURL(url);
  }
}
