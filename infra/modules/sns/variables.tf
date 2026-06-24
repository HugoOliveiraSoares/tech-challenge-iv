variable "topic_name" {
  description = "Name of the critical feedback SNS topic."
  type        = string
}

variable "tags" {
  description = "Tags applied to SNS resources."
  type        = map(string)
  default     = {}
}
