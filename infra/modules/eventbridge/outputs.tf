output "rule_name" {
  description = "EventBridge weekly report rule name."
  value       = aws_cloudwatch_event_rule.weekly_report.name
}

output "rule_arn" {
  description = "EventBridge weekly report rule ARN."
  value       = aws_cloudwatch_event_rule.weekly_report.arn
}
