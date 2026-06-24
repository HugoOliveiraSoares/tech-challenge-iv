output "api_base_url" {
  description = "Base URL for API calls."
  value       = module.api_gateway.stage_url
}

output "feedback_table_name" {
  description = "DynamoDB feedback table name."
  value       = module.dynamodb.table_name
}

output "critical_topic_arn" {
  description = "Critical feedback SNS topic ARN."
  value       = module.sns.topic_arn
}

output "feedback_api_lambda_name" {
  description = "Feedback API Lambda name."
  value       = module.feedback_api_lambda.function_name
}

output "critical_notifier_lambda_name" {
  description = "Critical notifier Lambda name."
  value       = module.critical_notifier_lambda.function_name
}

output "weekly_report_lambda_name" {
  description = "Weekly report Lambda name."
  value       = module.weekly_report_lambda.function_name
}

output "cloudwatch_dashboard_name" {
  description = "CloudWatch dashboard name."
  value       = module.cloudwatch.dashboard_name
}
