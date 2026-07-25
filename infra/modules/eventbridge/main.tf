data "aws_iam_policy_document" "scheduler_assume_role" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["scheduler.amazonaws.com"]
    }
  }
}

data "aws_iam_policy_document" "scheduler_invoke_lambda" {
  statement {
    actions   = ["lambda:InvokeFunction"]
    resources = [var.lambda_function_arn]
  }
}

resource "aws_iam_role" "scheduler" {
  name               = "${var.schedule_name}-scheduler-role"
  assume_role_policy = data.aws_iam_policy_document.scheduler_assume_role.json
  tags               = var.tags
}

resource "aws_iam_role_policy" "scheduler_invoke_lambda" {
  name   = "${var.schedule_name}-invoke-lambda"
  role   = aws_iam_role.scheduler.id
  policy = data.aws_iam_policy_document.scheduler_invoke_lambda.json
}

resource "aws_scheduler_schedule" "weekly_report" {
  name                         = var.schedule_name
  description                  = "Triggers the weekly feedback report Lambda."
  schedule_expression          = var.schedule_expression
  schedule_expression_timezone = var.schedule_timezone

  flexible_time_window {
    mode = "OFF"
  }

  target {
    arn      = var.lambda_function_arn
    role_arn = aws_iam_role.scheduler.arn
  }
}
