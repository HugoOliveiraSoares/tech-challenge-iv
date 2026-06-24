output "topic_arn" {
  description = "Critical feedback SNS topic ARN."
  value       = aws_sns_topic.critical_feedback.arn
}

output "topic_name" {
  description = "Critical feedback SNS topic name."
  value       = aws_sns_topic.critical_feedback.name
}
