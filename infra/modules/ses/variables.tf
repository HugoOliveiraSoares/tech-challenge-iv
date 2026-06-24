variable "email_from" {
  description = "SES sender identity to verify."
  type        = string
}

variable "admin_email_to" {
  description = "Administrative recipient identity to verify for sandbox accounts."
  type        = string
}
