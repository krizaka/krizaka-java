-- 2026-11-06 (krizaka-internal#2665): the code names leave the data. V1 seeded Sovkora's price keys and the media
-- worker's engine names under the product's former code name; V1 is applied and never edited (Flyway checksum), so the
-- rows are renamed here. The literals below are the one place that names the former prefix: this migration is history.
UPDATE billing_price
   SET price_key = 'sovkora.' || substring(price_key FROM 9)
 WHERE price_key LIKE 'orazaka.%';

UPDATE credit_pricebook
   SET model_name = 'sovkora-' || substring(model_name FROM 9)
 WHERE model_name LIKE 'orazaka-%';
