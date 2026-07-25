variable "aws_region" {
  description = "AWS region used by all resources."
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Environment name."
  type        = string
  default     = "prod"
}

variable "admin_email_to" {
  description = "Administrative e-mail that receives critical notifications and weekly reports."
  type        = string
}

variable "email_from" {
  description = "SES verified sender e-mail."
  type        = string
}

variable "log_level" {
  description = "Application log level."
  type        = string
  default     = "INFO"
}

variable "feedback_api_artifact_path" {
  description = "Feedback API Lambda zip path."
  type        = string
  default     = "../../../apps/feedback-api/target/function.zip"
}

variable "critical_notifier_artifact_path" {
  description = "Critical notifier Lambda zip path."
  type        = string
  default     = "../../../apps/critical-notifier/target/function.zip"
}

variable "weekly_report_artifact_path" {
  description = "Weekly report Lambda zip path."
  type        = string
  default     = "../../../apps/weekly-report/target/function.zip"
}

variable "lambda_handler" {
  description = "Default Quarkus Lambda handler."
  type        = string
  default     = "io.quarkus.amazon.lambda.runtime.QuarkusStreamHandler::handleRequest"
}

variable "weekly_report_schedule_expression" {
  description = "Weekly report schedule expression. Defaults to Sunday 23:59 UTC."
  type        = string
  default     = "cron(59 23 ? * SUN *)"
}

variable "cors_allowed_origins" {
  description = "Allowed CORS origins for API Gateway. Set explicit production domains."
  type        = list(string)
  default     = []
}
