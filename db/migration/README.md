# Flyway migration dialect strategy

P0-02 uses dialect split only where the common scripts cannot stay portable:

- `db/migration/` is the default MySQL 8.0 baseline and is also used for OceanBase MySQL mode.
- `db/migration-dm/` is the Dameng DM8 variant. It keeps the same version numbers and table/index names, replacing MySQL text/boolean-like column types with DM-compatible ANSI types such as `CLOB` and `SMALLINT`.

Flyway should select the target with `flyway.locations`:

```text
filesystem:db/migration
filesystem:db/migration-dm
```

The DM directory intentionally sits outside `db/migration/` because Flyway
filesystem locations scan nested directories recursively. Keeping it separate
prevents the default MySQL/OceanBase location from seeing duplicate migration
versions.

Application code must keep IDs generated outside the database and avoid handwritten `LIMIT`, `ON DUPLICATE KEY UPDATE`, `AUTO_INCREMENT`, `ON UPDATE CURRENT_TIMESTAMP`, and `JSON_*` SQL.
