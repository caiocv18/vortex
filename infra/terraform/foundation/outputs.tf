output "zone_id" {
  description = "Id of the public hosted zone."
  value       = aws_route53_zone.primary.zone_id
}

output "name_servers" {
  description = "Name servers to set at the domain registrar (delegation)."
  value       = aws_route53_zone.primary.name_servers
}

output "budget_name" {
  description = "Name of the gross monthly cost budget."
  value       = aws_budgets_budget.gross_monthly.name
}
