resource "aws_cloudwatch_event_rule" "weekly_report" {
  name                = var.schedule_name
  description         = "Triggers the weekly feedback report Lambda."
  schedule_expression = var.schedule_expression
  tags                = var.tags
}

resource "aws_cloudwatch_event_target" "weekly_report" {
  rule = aws_cloudwatch_event_rule.weekly_report.name
  arn  = var.lambda_function_arn
}

resource "aws_lambda_permission" "eventbridge" {
  statement_id  = "AllowExecutionFromEventBridge"
  action        = "lambda:InvokeFunction"
  function_name = var.lambda_function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.weekly_report.arn
}
