variable "environment" {
  description = "Deployment environment."
  type        = string
}

variable "lambda_function_names" {
  description = "Lambda function names monitored by standard alarms."
  type        = list(string)
}

variable "notification_topic_arn" {
  description = "SNS topic ARN used by alarms."
  type        = string
}

variable "critical_notifier_function_name" {
  description = "Critical notifier Lambda function name."
  type        = string
}

variable "weekly_report_function_name" {
  description = "Weekly report Lambda function name."
  type        = string
}

variable "tags" {
  description = "Tags applied to CloudWatch resources."
  type        = map(string)
  default     = {}
}
