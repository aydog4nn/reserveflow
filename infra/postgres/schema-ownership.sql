BEGIN;

REVOKE ALL ON SCHEMA public FROM PUBLIC;

SELECT format('ALTER SCHEMA public OWNER TO %I', :'owner')
\gexec

SELECT format('ALTER TABLE %I.%I OWNER TO %I', schemaname, tablename, :'owner')
FROM pg_tables
WHERE schemaname = 'public'
\gexec

SELECT format('ALTER SEQUENCE %I.%I OWNER TO %I', sequence_schema, sequence_name, :'owner')
FROM information_schema.sequences
WHERE sequence_schema = 'public'
\gexec

REVOKE ALL ON ALL TABLES IN SCHEMA public FROM PUBLIC;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM PUBLIC;

COMMIT;
