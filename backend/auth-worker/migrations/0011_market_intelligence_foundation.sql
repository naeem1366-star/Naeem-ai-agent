-- EMA Market Intelligence foundation. Read-only market data schema; no provider is enabled by this migration.
CREATE TABLE IF NOT EXISTS market_providers (
  id TEXT PRIMARY KEY,
  market TEXT NOT NULL CHECK (market IN ('crypto','currency','gold','iran_stock','global_stock','commodities')),
  display_name TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'unconfigured' CHECK (status IN ('active','degraded','disabled','unconfigured')),
  last_success_at INTEGER,
  last_error_at INTEGER,
  last_error_code TEXT,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_market_providers_market ON market_providers(market,status);

CREATE TABLE IF NOT EXISTS market_instruments (
  instrument_id TEXT PRIMARY KEY,
  market TEXT NOT NULL CHECK (market IN ('crypto','currency','gold','iran_stock','global_stock','commodities')),
  asset_class TEXT NOT NULL,
  symbol TEXT NOT NULL,
  display_name TEXT NOT NULL,
  exchange TEXT,
  country TEXT,
  quote_currency TEXT,
  unit TEXT,
  timezone TEXT,
  calendar_id TEXT,
  active INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0,1)),
  metadata_json TEXT NOT NULL DEFAULT '{}',
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_market_instruments_market_active ON market_instruments(market,active,symbol);

CREATE TABLE IF NOT EXISTS market_observations (
  id TEXT PRIMARY KEY,
  instrument_id TEXT NOT NULL REFERENCES market_instruments(instrument_id),
  data_type TEXT NOT NULL CHECK (data_type IN ('quote','reference_rate','fundamental','flow','sentiment')),
  observed_at INTEGER NOT NULL,
  received_at INTEGER NOT NULL,
  source_id TEXT NOT NULL,
  price_text TEXT,
  change_pct_text TEXT,
  volume_text TEXT,
  currency TEXT,
  unit TEXT,
  quality TEXT NOT NULL CHECK (quality IN ('verified','delayed','stale','partial','estimated','rejected')),
  freshness TEXT NOT NULL CHECK (freshness IN ('live','delayed','stale','historical','unavailable')),
  payload_hash TEXT,
  metadata_json TEXT NOT NULL DEFAULT '{}'
);
CREATE INDEX IF NOT EXISTS idx_market_observations_latest ON market_observations(instrument_id,data_type,observed_at DESC);
CREATE INDEX IF NOT EXISTS idx_market_observations_received ON market_observations(received_at DESC);

CREATE TABLE IF NOT EXISTS market_candles (
  instrument_id TEXT NOT NULL REFERENCES market_instruments(instrument_id),
  interval TEXT NOT NULL CHECK (interval IN ('1m','5m','15m','1h','4h','1d','1w','1mo')),
  open_time INTEGER NOT NULL,
  close_time INTEGER NOT NULL,
  open_text TEXT NOT NULL,
  high_text TEXT NOT NULL,
  low_text TEXT NOT NULL,
  close_text TEXT NOT NULL,
  volume_text TEXT,
  quote_volume_text TEXT,
  source_id TEXT NOT NULL,
  quality TEXT NOT NULL CHECK (quality IN ('verified','delayed','stale','partial')),
  is_final INTEGER NOT NULL DEFAULT 1 CHECK (is_final IN (0,1)),
  PRIMARY KEY(instrument_id,interval,open_time)
);
CREATE INDEX IF NOT EXISTS idx_market_candles_range ON market_candles(instrument_id,interval,open_time DESC);

CREATE TABLE IF NOT EXISTS market_analysis_reports (
  id TEXT PRIMARY KEY,
  user_id TEXT REFERENCES users(id),
  scope_type TEXT NOT NULL CHECK (scope_type IN ('instrument','market','cross_market')),
  scope_key TEXT NOT NULL,
  horizon TEXT NOT NULL,
  as_of INTEGER NOT NULL,
  created_at INTEGER NOT NULL,
  valid_until INTEGER,
  data_snapshot_refs TEXT NOT NULL DEFAULT '[]',
  engine_versions TEXT NOT NULL DEFAULT '{}',
  report_json TEXT NOT NULL,
  data_quality TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'completed' CHECK (status IN ('queued','running','completed','failed')),
  billing_usage_id TEXT
);
CREATE INDEX IF NOT EXISTS idx_market_reports_user_created ON market_analysis_reports(user_id,created_at DESC);

CREATE TABLE IF NOT EXISTS market_analysis_jobs (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id),
  job_type TEXT NOT NULL,
  idempotency_key TEXT NOT NULL,
  request_hash TEXT NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('queued','running','completed','failed','cancelled')),
  input_json TEXT NOT NULL,
  result_id TEXT REFERENCES market_analysis_reports(id),
  error_code TEXT,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  UNIQUE(user_id,idempotency_key)
);
CREATE INDEX IF NOT EXISTS idx_market_jobs_user_created ON market_analysis_jobs(user_id,created_at DESC);

CREATE TABLE IF NOT EXISTS market_forecasts (
  id TEXT PRIMARY KEY,
  instrument_id TEXT NOT NULL REFERENCES market_instruments(instrument_id),
  created_at INTEGER NOT NULL,
  target_time INTEGER NOT NULL,
  horizon TEXT NOT NULL,
  model_id TEXT NOT NULL,
  model_version TEXT NOT NULL,
  input_snapshot_refs TEXT NOT NULL,
  forecast_json TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','evaluated','invalidated'))
);
CREATE INDEX IF NOT EXISTS idx_market_forecasts_evaluation ON market_forecasts(instrument_id,target_time,status);

CREATE TABLE IF NOT EXISTS market_forecast_outcomes (
  forecast_id TEXT PRIMARY KEY REFERENCES market_forecasts(id),
  evaluated_at INTEGER NOT NULL,
  actual_value_text TEXT NOT NULL,
  error_metric_text TEXT,
  directional_result TEXT,
  evaluation_quality TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS market_watchlists (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id),
  name TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_market_watchlists_user ON market_watchlists(user_id,updated_at DESC);

CREATE TABLE IF NOT EXISTS market_watchlist_items (
  watchlist_id TEXT NOT NULL REFERENCES market_watchlists(id) ON DELETE CASCADE,
  instrument_id TEXT NOT NULL REFERENCES market_instruments(instrument_id),
  sort_order INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL,
  PRIMARY KEY(watchlist_id,instrument_id)
);

CREATE TABLE IF NOT EXISTS market_alerts (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id),
  instrument_id TEXT NOT NULL REFERENCES market_instruments(instrument_id),
  alert_type TEXT NOT NULL,
  condition_json TEXT NOT NULL,
  enabled INTEGER NOT NULL DEFAULT 1 CHECK (enabled IN (0,1)),
  last_triggered_at INTEGER,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_market_alerts_user_enabled ON market_alerts(user_id,enabled,created_at DESC);
