SELECT format('REVOKE ALL ON DATABASE %I FROM PUBLIC', :'database')
\gexec

SELECT format('REVOKE ALL ON DATABASE %I FROM %I', :'database', rolname)
FROM pg_roles
WHERE rolname IN ('inventory_app', 'order_app', 'payment_app')
  AND rolname <> :'owner'
\gexec

SELECT format('GRANT CONNECT ON DATABASE %I TO %I', :'database', :'owner')
\gexec
