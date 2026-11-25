# Network of the Dataflow workers (ADR 0008, amendment of 2026-11-25). A project whose Compute API is
# turned on gets a default network with open SSH and RDP rules, so the constraint goes on the folder
# before the API: projects.tf enables Compute only after it.

resource "google_org_policy_policy" "skip_default_network" {
  provider = google.billing
  count    = local.billing

  name   = "${google_folder.vortex.name}/policies/compute.skipDefaultNetworkCreation"
  parent = google_folder.vortex.name

  spec {
    rules {
      enforce = "TRUE"
    }
  }

  depends_on = [google_project_service.ingest_billing]
}

# No firewall rule on purpose: one worker with Dataflow Shuffle talks only to Google APIs, through the
# implied egress rule, and the implied ingress rule refuses everything else. Workers of a job without
# Shuffle would need tcp:12345-12346 between them.
resource "google_compute_network" "dataflow" {
  #checkov:skip=CKV2_GCP_18:No rule is the rule; the implied ones deny every ingress and allow the egress to Google APIs
  count = local.billing

  project                 = google_project.ingest.project_id
  name                    = "vortex-dataflow"
  description             = "Workers of the Vortex pipeline. No firewall rule: nothing enters."
  auto_create_subnetworks = false

  depends_on = [google_project_service.dataflow]
}

resource "google_compute_subnetwork" "dataflow" { # NOSONAR(terraform:S6258) same reason as the CKV_GCP_26 skip below; accepted risk
  #checkov:skip=CKV_GCP_26:Flow logs are billed per volume and one worker that talks only to Google APIs has nothing to show; Data Access audit logs already record those calls
  #checkov:skip=CKV_GCP_76:IPv4 only, the subnetwork has no IPv6 range
  count = local.billing

  project                  = google_project.ingest.project_id
  region                   = var.region
  name                     = "vortex-dataflow-${var.region}"
  network                  = google_compute_network.dataflow[0].id
  ip_cidr_range            = "10.10.0.0/28"
  private_ip_google_access = true
}
