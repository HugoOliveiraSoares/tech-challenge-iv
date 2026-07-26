output "api_id" {
  description = "API Gateway ID."
  value       = aws_apigatewayv2_api.this.id
}

output "api_endpoint" {
  description = "API Gateway base endpoint without stage path."
  value       = aws_apigatewayv2_api.this.api_endpoint
}
