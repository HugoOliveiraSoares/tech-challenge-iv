data "aws_caller_identity" "current" {}

locals {
  name_prefix = "feedback-platform-${var.environment}"

  common_tags = {
    Project     = "feedback-platform"
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

module "dynamodb" {
  source = "../../modules/dynamodb"

  table_name = "feedbacks-${var.environment}"
  tags       = local.common_tags
}

module "sns" {
  source = "../../modules/sns"

  topic_name = "feedback-critical-topic-${var.environment}"
  tags       = local.common_tags
}

module "ses" {
  source = "../../modules/ses"

  admin_email_to = var.admin_email_to
  email_from     = var.email_from
}

data "aws_iam_policy_document" "feedback_api" {
  statement {
    actions   = ["dynamodb:PutItem"]
    resources = [module.dynamodb.table_arn]
  }

  statement {
    actions   = ["sns:Publish"]
    resources = [module.sns.topic_arn]
  }
}

data "aws_iam_policy_document" "critical_notifier" {
  statement {
    actions   = ["ses:SendEmail", "ses:SendRawEmail"]
    resources = [module.ses.email_from_identity_arn]
  }
}

data "aws_iam_policy_document" "weekly_report" {
  statement {
    actions = ["dynamodb:Query", "dynamodb:Scan"]
    resources = [
      module.dynamodb.table_arn,
      module.dynamodb.data_envio_index_arn
    ]
  }

  statement {
    actions   = ["ses:SendEmail", "ses:SendRawEmail"]
    resources = [module.ses.email_from_identity_arn]
  }
}

module "feedback_api_lambda" {
  source = "../../modules/lambda"

  function_name = "feedback-api-${var.environment}"
  artifact_path = var.feedback_api_artifact_path
  handler       = var.lambda_handler
  timeout       = 30
  memory_size   = 512
  policy_json   = data.aws_iam_policy_document.feedback_api.json

  environment_variables = {
    AWS_REGION          = var.aws_region
    CRITICAL_TOPIC_ARN  = module.sns.topic_arn
    FEEDBACK_TABLE_NAME = module.dynamodb.table_name
    LOG_LEVEL           = var.log_level
  }

  tags = local.common_tags
}

module "critical_notifier_lambda" {
  source = "../../modules/lambda"

  function_name = "critical-notifier-${var.environment}"
  artifact_path = var.critical_notifier_artifact_path
  handler       = var.lambda_handler
  timeout       = 30
  memory_size   = 512
  policy_json   = data.aws_iam_policy_document.critical_notifier.json

  environment_variables = {
    ADMIN_EMAIL_TO = var.admin_email_to
    AWS_REGION     = var.aws_region
    EMAIL_FROM     = var.email_from
    LOG_LEVEL      = var.log_level
  }

  tags = local.common_tags
}

module "weekly_report_lambda" {
  source = "../../modules/lambda"

  function_name = "weekly-report-${var.environment}"
  artifact_path = var.weekly_report_artifact_path
  handler       = var.lambda_handler
  timeout       = 60
  memory_size   = 512
  policy_json   = data.aws_iam_policy_document.weekly_report.json

  environment_variables = {
    ADMIN_EMAIL_TO      = var.admin_email_to
    AWS_REGION          = var.aws_region
    EMAIL_FROM          = var.email_from
    FEEDBACK_TABLE_NAME = module.dynamodb.table_name
    LOG_LEVEL           = var.log_level
  }

  tags = local.common_tags
}

resource "aws_lambda_permission" "sns_critical_notifier" {
  statement_id  = "AllowExecutionFromCriticalFeedbackTopic"
  action        = "lambda:InvokeFunction"
  function_name = module.critical_notifier_lambda.function_name
  principal     = "sns.amazonaws.com"
  source_arn    = module.sns.topic_arn
}

resource "aws_sns_topic_subscription" "critical_notifier" {
  topic_arn = module.sns.topic_arn
  protocol  = "lambda"
  endpoint  = module.critical_notifier_lambda.function_arn

  depends_on = [aws_lambda_permission.sns_critical_notifier]
}

module "api_gateway" {
  source = "../../modules/api-gateway"

  name                 = "${local.name_prefix}-api"
  environment          = var.environment
  lambda_function_name = module.feedback_api_lambda.function_name
  lambda_invoke_arn    = module.feedback_api_lambda.invoke_arn
  cors_allowed_origins = var.cors_allowed_origins
  tags                 = local.common_tags
}

module "eventbridge" {
  source = "../../modules/eventbridge"

  schedule_name        = "${local.name_prefix}-weekly-report"
  schedule_expression  = var.weekly_report_schedule_expression
  lambda_function_name = module.weekly_report_lambda.function_name
  lambda_function_arn  = module.weekly_report_lambda.function_arn
  tags                 = local.common_tags
}

module "cloudwatch" {
  source = "../../modules/cloudwatch"

  environment                     = var.environment
  notification_topic_arn          = module.sns.topic_arn
  critical_notifier_function_name = module.critical_notifier_lambda.function_name
  weekly_report_function_name     = module.weekly_report_lambda.function_name
  lambda_function_names = [
    module.feedback_api_lambda.function_name,
    module.critical_notifier_lambda.function_name,
    module.weekly_report_lambda.function_name
  ]
  tags = local.common_tags
}
