output "state_bucket" {
  description = "Name of the bucket that holds every stack's state."
  value       = aws_s3_bucket.state.bucket
}

output "backend_hcl" {
  description = "Shared partial backend configuration; write it to ../backend.hcl (git-ignored)."
  value       = <<-EOT
    bucket       = "${aws_s3_bucket.state.bucket}"
    region       = "${data.aws_region.current.region}"
    use_lockfile = true
    encrypt      = true
  EOT
}
