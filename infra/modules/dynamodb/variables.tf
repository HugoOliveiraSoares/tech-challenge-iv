variable "table_name" {
  description = "Name of the feedbacks DynamoDB table."
  type        = string
}

variable "tags" {
  description = "Tags applied to DynamoDB resources."
  type        = map(string)
  default     = {}
}
