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
