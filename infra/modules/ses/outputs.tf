output "email_from_identity_arn" {
  description = "SES sender identity ARN."
  value       = aws_ses_email_identity.from.arn
}

output "admin_email_identity_arns" {
  description = "SES admin recipient identity ARNs, when created."
  value       = aws_ses_email_identity.admin[*].arn
}
