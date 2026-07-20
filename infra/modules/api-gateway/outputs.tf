output "api_id" {
  description = "API Gateway ID."
  value       = aws_api_gateway_rest_api.this.id
}

output "api_endpoint" {
  description = "API Gateway base endpoint without stage path."
  value       = var.local_execute_api_domain != null ? "http://${aws_api_gateway_rest_api.this.id}.execute-api.${var.local_execute_api_domain}" : "https://${aws_api_gateway_rest_api.this.id}.execute-api.${var.aws_region}.amazonaws.com"
}

output "stage_url" {
  description = "API Gateway stage URL."
  value       = var.local_execute_api_domain != null ? "http://${aws_api_gateway_rest_api.this.id}.execute-api.${var.local_execute_api_domain}/${var.environment}" : "https://${aws_api_gateway_rest_api.this.id}.execute-api.${var.aws_region}.amazonaws.com/${var.environment}"
}
