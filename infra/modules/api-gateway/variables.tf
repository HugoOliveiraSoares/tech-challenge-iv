variable "name" {
  description = "API Gateway name."
  type        = string
}

variable "environment" {
  description = "Stage name."
  type        = string
}

variable "aws_region" {
  description = "AWS region used to render API Gateway invoke URLs."
  type        = string
}

variable "lambda_function_name" {
  description = "Feedback API Lambda function name."
  type        = string
}

variable "lambda_invoke_arn" {
  description = "Feedback API Lambda invoke ARN."
  type        = string
}

variable "local_execute_api_domain" {
  description = "Local fakecloud execute-api domain used for API Gateway data-plane calls. Leave null for AWS environments."
  type        = string
  default     = null
}

variable "throttling_burst_limit" {
  description = "Default API throttling burst limit."
  type        = number
  default     = 100
}

variable "throttling_rate_limit" {
  description = "Default API throttling rate limit."
  type        = number
  default     = 50
}

variable "cors_allowed_origins" {
  description = "Allowed CORS origins for the API Gateway stage. Use explicit domains in production."
  type        = list(string)
  default     = ["*"]
}

variable "tags" {
  description = "Tags applied to API Gateway resources."
  type        = map(string)
  default     = {}
}
