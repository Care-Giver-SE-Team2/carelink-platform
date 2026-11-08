-- created_at was left to DEFAULT CURRENT_TIMESTAMP, which MySQL fills in UTC. Every other time
-- this service keeps goes through the driver with connectionTimeZone=Asia/Singapore, which stores
-- a Singapore time eight hours ahead of it and takes the eight hours off again on reading. A
-- created_at in UTC skipped the first shift but not the second, so it read back eight hours early
-- (a request made at 16:24 showed as asked at 00:24).
--
-- The application now writes created_at itself. The rows the default wrote are moved onto the
-- same footing: UTC + 8 is Singapore time, + 8 again is what the driver would have stored.
UPDATE value_added_service_request SET created_at = created_at + INTERVAL 16 HOUR;
