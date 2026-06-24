variable "schedule_name" {
  description = "EventBridge rule name for weekly reports."
  type        = string
}

variable "schedule_expression" {
  description = "EventBridge schedule expression."
  type        = string
  default     = "cron(59 23 ? * SUN *)"
}

variable "lambda_function_name" {
  description = "Weekly report Lambda function name."
  type        = string
}

variable "lambda_function_arn" {
  description = "Weekly report Lambda function ARN."
  type        = string
}

variable "tags" {
  description = "Tags applied to EventBridge resources."
  type        = map(string)
  default     = {}
}
