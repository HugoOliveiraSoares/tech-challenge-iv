output "schedule_name" {
  description = "EventBridge Scheduler weekly report schedule name."
  value       = aws_scheduler_schedule.weekly_report.name
}

output "schedule_arn" {
  description = "EventBridge Scheduler weekly report schedule ARN."
  value       = aws_scheduler_schedule.weekly_report.arn
}
