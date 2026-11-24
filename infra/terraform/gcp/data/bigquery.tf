locals {
  schemas = "${path.module}/../../../../data/schemas"

  # One entry per base table: dataset, schema file, clustering column
  tables = {
    raw_movements       = { dataset = "raw", cluster = "product_id" }
    raw_user_events     = { dataset = "raw", cluster = "event_type" }
    curated_movements   = { dataset = "curated", cluster = "product_id" }
    curated_user_events = { dataset = "curated", cluster = "event_type" }
  }
  dataset_ids = {
    raw     = google_bigquery_dataset.raw.dataset_id
    curated = google_bigquery_dataset.curated.dataset_id
  }

  # Columns per raw table that carry the policy tag, once billing allows it. The provider sends an
  # empty tag for every column that has none here, so a tag set outside Terraform does not last.
  pii_columns = jsondecode(file("${path.module}/../../../../data/policy_tags.json"))
  tagged      = { for table, columns in local.pii_columns : table => columns if !local.sandbox }
  table_schemas = {
    for table in keys(local.tables) : table => contains(keys(local.tagged), table) ? jsonencode([
      for field in jsondecode(file("${local.schemas}/${table}.json")) : merge(field, {
        for key in ["policyTags"] : key => { names = google_data_catalog_policy_tag.pii[*].name }
        if contains(local.tagged[table], field.name)
      })
    ]) : file("${local.schemas}/${table}.json")
  }
}

# Personal data in clear. Partitions go after raw_retention_days, and the personal columns
# carry the policy tag of policy_tags.tf.
resource "google_bigquery_dataset" "raw" {
  #checkov:skip=CKV_GCP_81:A customer managed key needs Cloud KMS, which needs billing; data is encrypted at rest with Google-managed keys
  project                         = google_project.ingest.project_id
  dataset_id                      = "vortex_raw"
  friendly_name                   = "Vortex raw events"
  description                     = "Events as received, personal data in clear. Only the pipeline writes here; no analyst reads it."
  location                        = var.region
  default_table_expiration_ms     = local.sandbox_expiration_ms
  default_partition_expiration_ms = local.raw_retention_ms

  depends_on = [google_project_service.ingest]
}

resource "google_bigquery_dataset" "curated" {
  #checkov:skip=CKV_GCP_81:A customer managed key needs Cloud KMS, which needs billing; data is encrypted at rest with Google-managed keys
  project                         = google_project.ingest.project_id
  dataset_id                      = "vortex_curated"
  friendly_name                   = "Vortex curated events"
  description                     = "Pseudonymized events: HMAC keys instead of usernames and e-mails, networks instead of IP addresses. Read through the authorized views of vortex_analytics."
  location                        = var.region
  default_table_expiration_ms     = local.sandbox_expiration_ms
  default_partition_expiration_ms = local.sandbox_expiration_ms

  depends_on = [google_project_service.ingest]
}

resource "google_bigquery_dataset" "analytics" {
  #checkov:skip=CKV_GCP_81:Holds views only, no data of its own
  project                         = google_project.analytics.project_id
  dataset_id                      = "vortex_analytics"
  friendly_name                   = "Vortex analytics"
  description                     = "Views over vortex_curated, authorized on it; analysts read these and never the base tables. Only Terraform creates views here."
  location                        = var.region
  default_table_expiration_ms     = local.sandbox_expiration_ms
  default_partition_expiration_ms = local.sandbox_expiration_ms

  depends_on = [google_project_service.analytics]
}

resource "google_bigquery_table" "base" {
  #checkov:skip=CKV_GCP_80:A customer managed key needs Cloud KMS, which needs billing; data is encrypted at rest with Google-managed keys
  for_each = local.tables

  project             = google_project.ingest.project_id
  dataset_id          = local.dataset_ids[each.value.dataset]
  table_id            = each.key
  description         = "Schema in data/schemas/${each.key}.json. Loads always append: a truncating load or CREATE OR REPLACE drops the column policy tags."
  schema              = local.table_schemas[each.key]
  clustering          = [each.value.cluster]
  deletion_protection = true

  time_partitioning {
    type          = "DAY"
    field         = "event_ts"
    expiration_ms = each.value.dataset == "raw" ? local.raw_retention_ms : local.curated_partition_ms
  }
}

locals {
  curated_movements   = "${google_project.ingest.project_id}.${google_bigquery_dataset.curated.dataset_id}.${google_bigquery_table.base["curated_movements"].table_id}"
  curated_user_events = "${google_project.ingest.project_id}.${google_bigquery_dataset.curated.dataset_id}.${google_bigquery_table.base["curated_user_events"].table_id}"
  analytics_dataset   = "${google_project.analytics.project_id}.${google_bigquery_dataset.analytics.dataset_id}"
}

# Loads append, and a rerun appends the same events again, so the views keep the
# newest copy of each event_id. Columns are listed one by one: a column added to the
# curated table stays out of reach until a view names it.
resource "google_bigquery_table" "v_movements" {
  #checkov:skip=CKV_GCP_80:A view stores no data
  project             = google_project.analytics.project_id
  dataset_id          = google_bigquery_dataset.analytics.dataset_id
  table_id            = "v_movements"
  description         = "Stock movements, one row per event, user as a pseudonym."
  deletion_protection = true
  expiration_time     = local.view_expiration_time

  view {
    use_legacy_sql = false
    query          = <<-SQL
      SELECT
        event_id, event_ts, movement_id, product_id, product_description, product_type,
        movement_type, quantity, sale_value, supplier_value, profit, stock_before, stock_after,
        movement_ts, user_key, hmac_key_version
      FROM `${local.curated_movements}`
      WHERE TRUE
      QUALIFY ROW_NUMBER() OVER (PARTITION BY event_id ORDER BY ingested_at DESC) = 1
    SQL
  }
}

resource "google_bigquery_table" "v_user_events" {
  #checkov:skip=CKV_GCP_80:A view stores no data
  project             = google_project.analytics.project_id
  dataset_id          = google_bigquery_dataset.analytics.dataset_id
  table_id            = "v_user_events"
  description         = "Authorization events, one row per event, user and e-mail as pseudonyms, network instead of IP address."
  deletion_protection = true
  expiration_time     = local.view_expiration_time

  view {
    use_legacy_sql = false
    query          = <<-SQL
      SELECT
        event_id, event_type, event_ts, user_key, email_key, ip_prefix, attributes, hmac_key_version
      FROM `${local.curated_user_events}`
      WHERE TRUE
      QUALIFY ROW_NUMBER() OVER (PARTITION BY event_id ORDER BY ingested_at DESC) = 1
    SQL
  }
}

# The join that pseudonymization keeps possible: who moved stock and how often
# they log in, without anyone reading a username.
resource "google_bigquery_table" "v_user_activity" {
  #checkov:skip=CKV_GCP_80:A view stores no data
  project             = google_project.analytics.project_id
  dataset_id          = google_bigquery_dataset.analytics.dataset_id
  table_id            = "v_user_activity"
  description         = "Per pseudonymous user: movements, units in and out, logins and the last login."
  deletion_protection = true
  expiration_time     = local.view_expiration_time

  view {
    use_legacy_sql = false
    query          = <<-SQL
      WITH movements AS (
        SELECT
          user_key,
          COUNT(*) AS movements,
          SUM(IF(movement_type = 'ENTRADA', quantity, 0)) AS units_in,
          SUM(IF(movement_type = 'SAIDA', quantity, 0)) AS units_out
        FROM `${local.analytics_dataset}.${google_bigquery_table.v_movements.table_id}`
        WHERE user_key IS NOT NULL
        GROUP BY user_key
      ),
      logins AS (
        SELECT
          user_key,
          COUNTIF(event_type = 'user.logged_in') AS logins,
          MAX(IF(event_type = 'user.logged_in', event_ts, NULL)) AS last_login_at
        FROM `${local.analytics_dataset}.${google_bigquery_table.v_user_events.table_id}`
        WHERE user_key IS NOT NULL
        GROUP BY user_key
      )
      SELECT
        user_key,
        COALESCE(movements.movements, 0) AS movements,
        COALESCE(movements.units_in, 0) AS units_in,
        COALESCE(movements.units_out, 0) AS units_out,
        COALESCE(logins.logins, 0) AS logins,
        logins.last_login_at
      FROM movements
      FULL OUTER JOIN logins USING (user_key)
    SQL
  }
}

resource "google_bigquery_table" "v_daily_sales" {
  #checkov:skip=CKV_GCP_80:A view stores no data
  project             = google_project.analytics.project_id
  dataset_id          = google_bigquery_dataset.analytics.dataset_id
  table_id            = "v_daily_sales"
  description         = "Sales per day (America/Sao_Paulo) and product, with no user at all."
  deletion_protection = true
  expiration_time     = local.view_expiration_time

  view {
    use_legacy_sql = false
    query          = <<-SQL
      SELECT
        DATE(event_ts, 'America/Sao_Paulo') AS sale_date,
        product_id,
        ANY_VALUE(product_description) AS product_description,
        SUM(quantity) AS units,
        SUM(sale_value * quantity) AS revenue,
        SUM(profit) AS profit
      FROM `${local.analytics_dataset}.${google_bigquery_table.v_movements.table_id}`
      WHERE movement_type = 'SAIDA'
      GROUP BY sale_date, product_id
    SQL
  }
}
