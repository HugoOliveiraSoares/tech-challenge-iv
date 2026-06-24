resource "aws_sns_topic" "critical_feedback" {
  name = var.topic_name
  tags = var.tags
}
