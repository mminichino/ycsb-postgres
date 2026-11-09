# PostgreSQL YCSB 1.0.0
This package is a YCSB implementation to test PostgreSQL and compatible platforms (including Databricks Lakebase). It is based on YCSB core 0.18.6.

## Requirements
- Java 17 JRE or JDK
- Linux, macOS, or Windows load generator system (8 cores / 32GB RAM minimum recommended)
- PostgreSQL 15–18 (or a wire-compatible service such as Databricks Lakebase)

## Quickstart

### 1. Set up YCSB
Download the distribution to begin testing.
```
curl -OLs https://github.com/mminichino/ycsb-postgres/releases/download/v1.0.0/ycsb-postgres-1.0.0.zip
```
```
unzip ycsb-postgres-1.0.0.zip
```
```
cd ycsb-postgres-1.0.0
```
Edit the `conf/db.properties` properties file to configure database settings.<br>
On Linux/macOS:
```
vi conf/db.properties
```
On Windows:
```
notepad conf\db.properties
```
To run all test scenarios:<br>
On Linux/macOS:
```
bin/ycsb-postgres
```
On Windows:
```
bin\ycsb-postgres.bat
```

## Data model
Records use the classic YCSB JDBC relational layout (no JSON embedding):

- Primary key column: `YCSB_KEY`
- Field columns: `FIELD0` … `FIELD{N-1}` as `TEXT`, where `N` comes from workload `fieldcount` (default 10)
- Updates set only the fields present in the write
- Scans use `WHERE YCSB_KEY >= ? ORDER BY YCSB_KEY LIMIT ?`

`RunPrep` drops and recreates the table before each benchmark run.

## PostgreSQL Configuration Parameters

- `postgres.host` — Hostname (default: `localhost`)
- `postgres.port` — Port (default: `5432`)
- `postgres.database` — Database name (default: `ycsb`)
- `postgres.table` — Table name (default: `usertable`)
- `postgres.username` / `postgres.password` — Credentials
- `postgres.jdbcUrl` — Optional full JDBC URL; when set, overrides host/port/database/`sslmode` assembly
- `postgres.ssl` — Hint used when choosing a default `sslmode` (default: `false`)
- `postgres.sslmode` — libpq SSL mode passed on the JDBC URL (default: `prefer`, or `require` when `postgres.ssl=true`)
- `postgres.auth` — `password` (default) or `oauth` for Databricks Lakebase service-principal auth
- `postgres.pool.size` — HikariCP maximum pool size (default: `32`)
- `postgres.debug` — Override logging to DEBUG level (`true` / `false`, default: `false`)
- `batchsize` — Insert batch size during load (default: `1`)
- `fieldcount` / `fieldnameprefix` — Used by prep to create field columns (defaults: `10` / `field`)

### Databricks Lakebase OAuth
Two Lakebase auth patterns are supported:

**1. Token-as-password** (CLI / personal OAuth token):

- `JDBC_DATABASE_URL` / `DATABASE_URL` — libpq-style URL, e.g. `postgresql://user@host/databricks_postgres?sslmode=require`
- `OAUTH_TOKEN` — used as the Postgres password

**2. Service-principal SDK rotation** (`postgres.auth=oauth`):

- `databricks.host` — Workspace URL (or `DATABRICKS_HOST`)
- `databricks.clientId` — Service principal client ID (or `DATABRICKS_CLIENT_ID`)
- `databricks.clientSecret` — Service principal secret (or `DATABRICKS_CLIENT_SECRET`)
- `databricks.endpoint` — Lakebase endpoint name `projects/.../branches/.../endpoints/...` (or `DATABRICKS_ENDPOINT` / `ENDPOINT_NAME`)
- `postgres.username` — Same UUID as the service principal client ID
- `postgres.sslmode=require` (recommended)

With SDK OAuth, the pool mints a fresh database credential via the Databricks SDK for Java on each new connection and recycles connections before the typical 60-minute token lifetime.
