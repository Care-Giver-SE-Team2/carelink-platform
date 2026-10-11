#!/bin/sh
# Run by docker-compose.yml's mysql-setup on every start, once MySQL is up: each service's own database
# and the account that service connects with, which can reach that database only
# (docs/platform/service-boundaries.md, section 9). core's database and account are the image's own
# (MYSQL_DATABASE, MYSQL_USER). Safe to run again: it creates what is missing and resets the passwords.
#
# The local stand-in for what the cloud environment sets up on RDS: one server, a database per service.
set -eu

mysql -h mysql -uroot -p"$MYSQL_ROOT_PASSWORD" <<SQL
CREATE DATABASE IF NOT EXISTS carelink_visit CHARACTER SET utf8mb4;
CREATE USER IF NOT EXISTS 'visit'@'%' IDENTIFIED BY '$MYSQL_PASSWORD';
ALTER USER 'visit'@'%' IDENTIFIED BY '$MYSQL_PASSWORD';
GRANT ALL PRIVILEGES ON carelink_visit.* TO 'visit'@'%';

CREATE DATABASE IF NOT EXISTS carelink_report CHARACTER SET utf8mb4;
CREATE USER IF NOT EXISTS 'report'@'%' IDENTIFIED BY '$MYSQL_PASSWORD';
ALTER USER 'report'@'%' IDENTIFIED BY '$MYSQL_PASSWORD';
GRANT ALL PRIVILEGES ON carelink_report.* TO 'report'@'%';

CREATE DATABASE IF NOT EXISTS carelink_notification CHARACTER SET utf8mb4;
CREATE USER IF NOT EXISTS 'notification'@'%' IDENTIFIED BY '$MYSQL_PASSWORD';
ALTER USER 'notification'@'%' IDENTIFIED BY '$MYSQL_PASSWORD';
GRANT ALL PRIVILEGES ON carelink_notification.* TO 'notification'@'%';
SQL

echo "service databases ready: carelink_visit, carelink_report, carelink_notification"
