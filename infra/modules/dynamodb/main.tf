resource "aws_dynamodb_table" "feedbacks" {
  name         = var.table_name
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "id"

  attribute {
    name = "id"
    type = "S"
  }

  attribute {
    name = "periodo"
    type = "S"
  }

  attribute {
    name = "dataEnvio"
    type = "S"
  }

  global_secondary_index {
    name            = "dataEnvio-index"
    hash_key        = "periodo"
    range_key       = "dataEnvio"
    projection_type = "ALL"
  }

  point_in_time_recovery {
    enabled = true
  }

  server_side_encryption {
    enabled = true
  }

  tags = var.tags
}

resource "aws_dynamodb_table" "processing_control" {
  name         = "feedback-processing-control-${var.environment}"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "periodo"

  attribute {
    name = "periodo"
    type = "S"
  }

  point_in_time_recovery {
    enabled = true
  }

  server_side_encryption {
    enabled = true
  }

  tags = var.tags
}
