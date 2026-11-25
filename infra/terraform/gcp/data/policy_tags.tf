# Column-level access on the raw tables' personal data (ADR 0008, amendment of 2026-11-24).
# Whoever may read vortex_raw still needs Fine-Grained Reader on the tag to read a tagged column;
# neither Owner nor Organization Administrator carries it. Writing needs nothing: an append keeps the
# tags and never checks them. The tagged columns are listed in data/policy_tags.json, read by
# bigquery.tf and by data/scripts/sandbox.sh prove.

resource "google_data_catalog_taxonomy" "pii" {
  count    = local.billing
  provider = google.billing

  project      = google_project.ingest.project_id
  region       = var.region # a tag only works on tables of its own location
  display_name = "vortex-pii"
  description  = "Personal data of the Vortex events."

  # Without it the tag is only a label
  activated_policy_types = ["FINE_GRAINED_ACCESS_CONTROL"]

  # Deleting a taxonomy or a tag also takes it off every column
  deletion_policy = "PREVENT"

  depends_on = [google_project_service.ingest_billing]
}

resource "google_data_catalog_policy_tag" "pii" {
  count    = local.billing
  provider = google.billing

  taxonomy        = google_data_catalog_taxonomy.pii[0].id
  display_name    = "pii"
  description     = "Personal data in clear, or open text that may hold it."
  deletion_policy = "PREVENT"
}

# The only reader. pipeline-launcher writes the raw tables and is refused on these columns.
resource "google_data_catalog_policy_tag_iam_member" "operator_reads_pii" {
  count    = local.billing
  provider = google.billing

  policy_tag = google_data_catalog_policy_tag.pii[0].name
  role       = "roles/datacatalog.categoryFineGrainedReader"
  member     = var.operator_member
}
