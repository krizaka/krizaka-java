-- ============================================================================
-- SOVKORA — Local DB bootstrap · 10 — IDENTITY CONTEXT
-- ----------------------------------------------------------------------------
-- Owner: Identity service (krizaka-users-core + persistence-identity).
-- Everything "who is the actor and what may they do": users, credentials,
-- authorities, tokens, profiles, rate-limit tiers, per-user model prefs.
-- Other contexts reference the user ONLY by an opaque ActorId — no inbound or
-- outbound cross-context FK. Phase 2 cutover: these tables live in their OWN
-- database (krizaka_users_db) under the service's own role, created here.
-- ============================================================================

-- The password is NOT set here. psql 15 cannot read the environment (\getenv is 16+)
-- and ERR-125 bans a shell script, so `sovkora start` applies ALTER ROLE from
-- IDENTITY_DB_PASSWORD once the container is healthy. A role created without a
-- password cannot authenticate, so a skipped step fails closed rather than leaving a
-- guessable one — which is what the committed literal was (ADR-035, audit #5).
CREATE ROLE krizaka_users LOGIN;
CREATE DATABASE krizaka_users_db OWNER krizaka_users;
-- The schema is NOT here any more (krizaka-internal#2255): the service creates it at startup with Flyway
-- (krizaka-users/krizaka-users-persistence/src/main/resources/db/migration/). This file only creates the role and the database, locally.
