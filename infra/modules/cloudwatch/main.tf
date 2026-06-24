resource "aws_cloudwatch_metric_alarm" "lambda_errors" {
  for_each = toset(var.lambda_function_names)

  alarm_name          = "${each.value}-errors"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "Errors"
  namespace           = "AWS/Lambda"
  period              = 300
  statistic           = "Sum"
  threshold           = 0
  alarm_description   = "Lambda ${each.value} returned errors in the last 5 minutes."
  alarm_actions       = [var.notification_topic_arn]
  treat_missing_data  = "notBreaching"

  dimensions = {
    FunctionName = each.value
  }

  tags = var.tags
}

resource "aws_cloudwatch_metric_alarm" "lambda_throttles" {
  for_each = toset(var.lambda_function_names)

  alarm_name          = "${each.value}-throttles"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "Throttles"
  namespace           = "AWS/Lambda"
  period              = 300
  statistic           = "Sum"
  threshold           = 0
  alarm_description   = "Lambda ${each.value} was throttled in the last 5 minutes."
  alarm_actions       = [var.notification_topic_arn]
  treat_missing_data  = "notBreaching"

  dimensions = {
    FunctionName = each.value
  }

  tags = var.tags
}

resource "aws_cloudwatch_metric_alarm" "notification_failure" {
  alarm_name          = "${var.environment}-notification-failure"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "NotificationFailureCount"
  namespace           = "FeedbackPlatform"
  period              = 300
  statistic           = "Sum"
  threshold           = 0
  alarm_description   = "Critical notification failures detected."
  alarm_actions       = [var.notification_topic_arn]
  treat_missing_data  = "notBreaching"

  dimensions = {
    Service = var.critical_notifier_function_name
  }

  tags = var.tags
}

resource "aws_cloudwatch_metric_alarm" "weekly_report_failure" {
  alarm_name          = "${var.environment}-weekly-report-failure"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "WeeklyReportFailureCount"
  namespace           = "FeedbackPlatform"
  period              = 300
  statistic           = "Sum"
  threshold           = 0
  alarm_description   = "Weekly report failures detected."
  alarm_actions       = [var.notification_topic_arn]
  treat_missing_data  = "notBreaching"

  dimensions = {
    Service = var.weekly_report_function_name
  }

  tags = var.tags
}

resource "aws_cloudwatch_dashboard" "this" {
  dashboard_name = "feedback-platform-${var.environment}"

  dashboard_body = jsonencode({
    widgets = [
      {
        type   = "metric"
        x      = 0
        y      = 0
        width  = 12
        height = 6
        properties = {
          title   = "Lambda Invocations and Errors"
          view    = "timeSeries"
          region  = data.aws_region.current.name
          metrics = flatten([for name in var.lambda_function_names : [["AWS/Lambda", "Invocations", "FunctionName", name], [".", "Errors", ".", name]]])
        }
      },
      {
        type   = "metric"
        x      = 12
        y      = 0
        width  = 12
        height = 6
        properties = {
          title   = "Business Metrics"
          view    = "timeSeries"
          region  = data.aws_region.current.name
          metrics = [["FeedbackPlatform", "FeedbackReceivedCount"], [".", "CriticalFeedbackCount"], [".", "NotificationFailureCount"], [".", "WeeklyReportFailureCount"]]
        }
      }
    ]
  })
}

data "aws_region" "current" {}
