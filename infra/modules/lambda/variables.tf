variable "function_name" {
  description = "Lambda function name."
  type        = string
}

variable "artifact_path" {
  description = "Path to the Lambda deployment zip."
  type        = string
}

variable "handler" {
  description = "Lambda handler."
  type        = string
}

variable "runtime" {
  description = "Lambda runtime."
  type        = string
  default     = "java21"
}

variable "memory_size" {
  description = "Lambda memory in MB."
  type        = number
  default     = 512
}

variable "timeout" {
  description = "Lambda timeout in seconds."
  type        = number
  default     = 30
}

variable "environment_variables" {
  description = "Lambda environment variables."
  type        = map(string)
  default     = {}
}

variable "policy_json" {
  description = "IAM policy JSON with function-specific permissions."
  type        = string
}

variable "log_retention_in_days" {
  description = "CloudWatch log retention in days."
  type        = number
  default     = 14
}

variable "tags" {
  description = "Tags applied to Lambda resources."
  type        = map(string)
  default     = {}
}
