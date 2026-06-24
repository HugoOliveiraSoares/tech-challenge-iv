resource "aws_ses_email_identity" "from" {
  email = var.email_from
}

resource "aws_ses_email_identity" "admin" {
  count = var.admin_email_to == var.email_from ? 0 : 1
  email = var.admin_email_to
}
