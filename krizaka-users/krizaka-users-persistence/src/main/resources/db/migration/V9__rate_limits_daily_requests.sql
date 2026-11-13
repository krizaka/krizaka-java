-- A daily request quota per rate-limit tier, read by the edge through the token exchange.
-- NULL means "the edge's own default"; a set value is bounded to 1..10 000 000 requests a day.
ALTER TABLE rate_limits ADD COLUMN IF NOT EXISTS daily_requests INT;
ALTER TABLE rate_limits DROP CONSTRAINT IF EXISTS rate_limits_daily_requests_bounds;
ALTER TABLE rate_limits ADD CONSTRAINT rate_limits_daily_requests_bounds
    CHECK (daily_requests IS NULL OR daily_requests BETWEEN 1 AND 10000000);
