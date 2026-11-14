# Plain strings, one per site and field, so scripts/publicar-fronts.sh reads them with
# terraform output -raw and needs no jq.
output "app_bucket" {
  description = "Bucket of the inventory app."
  value       = module.spa["app"].bucket
}

output "app_distribution_id" {
  description = "Distribution of the inventory app."
  value       = module.spa["app"].distribution_id
}

output "app_url" {
  description = "Public URL of the inventory app."
  value       = module.spa["app"].url
}

output "login_bucket" {
  description = "Bucket of the login app."
  value       = module.spa["login"].bucket
}

output "login_distribution_id" {
  description = "Distribution of the login app."
  value       = module.spa["login"].distribution_id
}

output "login_url" {
  description = "Public URL of the login app."
  value       = module.spa["login"].url
}
