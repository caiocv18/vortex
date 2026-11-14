output "instance_id" {
  description = "Id of the demo instance, or null while demo_enabled is false."
  value       = one(aws_instance.demo[*].id)
}

output "public_ipv4" {
  description = "Public IPv4 address of the demo instance, or null while demo_enabled is false. It changes on every launch."
  value       = one(aws_instance.demo[*].public_ip)
}

output "data_volume_id" {
  description = "Id of the data volume, which survives the instance."
  value       = aws_ebs_volume.data.id
}

output "security_group_id" {
  description = "Id of the demo-origin security group (IPv4 ingress and all egress)."
  value       = aws_security_group.origin.id
}

output "security_group_ipv6_id" {
  description = "Id of the demo-origin-ipv6 security group (IPv6 ingress only)."
  value       = aws_security_group.origin_ipv6.id
}

output "availability_zone" {
  description = "Name of the availability zone the subnet and the data volume live in."
  value       = aws_subnet.public.availability_zone
}
