CREATE TABLE IF NOT EXISTS loan_products (
  id              UUID PRIMARY KEY,
  name            TEXT NOT NULL,
  min_amount      NUMERIC NOT NULL,
  max_amount      NUMERIC NOT NULL,
  min_term_months INT NOT NULL,
  max_term_months INT NOT NULL,
  apr             NUMERIC NOT NULL,
  purpose         TEXT NOT NULL,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS users (
  email          TEXT PRIMARY KEY,
  display_name   TEXT NOT NULL,
  monthly_income NUMERIC NOT NULL,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  correlation_id TEXT NOT NULL,
  user_uuid      TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS user_identities (
  user_uuid      TEXT PRIMARY KEY,
  email          TEXT REFERENCES users(email),
  first_seen_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  correlation_id TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS loan_requests (
  id             UUID PRIMARY KEY,
  amount         NUMERIC NOT NULL,
  term_months    INT NOT NULL,
  purpose        TEXT NOT NULL,
  user_uuid      TEXT NOT NULL,
  correlation_id TEXT NOT NULL,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS loan_applications (
  id              UUID PRIMARY KEY,
  loan_request_id UUID NOT NULL REFERENCES loan_requests(id),
  product_id      UUID NOT NULL REFERENCES loan_products(id),
  email           TEXT REFERENCES users(email),
  status          TEXT NOT NULL,
  user_uuid       TEXT NOT NULL,
  correlation_id  TEXT NOT NULL,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
