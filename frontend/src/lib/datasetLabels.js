/**
 * Shared user-facing labels for backend dataset-version enums
 * (DatasetVersionResponse#source / #adjustmentBasis), so every page
 * presents the same terminology instead of each defining its own copy.
 */

export const SOURCE_LABELS = {
  CSV_UPLOAD: 'CSV upload',
  ALPHA_VANTAGE: 'Alpha Vantage',
};

export const ADJUSTMENT_BASIS_LABELS = {
  RAW: 'Raw',
  SPLIT_ADJUSTED: 'Split-adjusted',
  SPLIT_AND_DIVIDEND_ADJUSTED: 'Split & dividend-adjusted',
};
