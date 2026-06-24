output "table_name" {
  description = "DynamoDB table name."
  value       = aws_dynamodb_table.feedbacks.name
}

output "table_arn" {
  description = "DynamoDB table ARN."
  value       = aws_dynamodb_table.feedbacks.arn
}

output "data_envio_index_arn" {
  description = "DynamoDB weekly report GSI ARN."
  value       = "${aws_dynamodb_table.feedbacks.arn}/index/dataEnvio-index"
}
