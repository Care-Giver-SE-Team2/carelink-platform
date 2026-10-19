-- FICTIONAL local credential-demo ONLY; never part of Flyway or a real institution catalogue.
INSERT INTO credential_type(name)
 SELECT 'Demo First aid (fictional)' WHERE NOT EXISTS(SELECT 1 FROM credential_type WHERE name='Demo First aid (fictional)');
INSERT INTO credential_type(name)
 SELECT 'Demo Nursing (fictional)' WHERE NOT EXISTS(SELECT 1 FROM credential_type WHERE name='Demo Nursing (fictional)');
INSERT INTO credential_type(name)
 SELECT 'Demo Permanent qualification (fictional)' WHERE NOT EXISTS(SELECT 1 FROM credential_type WHERE name='Demo Permanent qualification (fictional)');
