terraform {
  required_version = ">= 1.6.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region     = var.aws_region
  access_key = local.fakecloud_access_key
  secret_key = local.fakecloud_secret_key

  # Local fakecloud endpoints. Use infra/environments/prod for real AWS resources.
  endpoints {
    apigateway   = local.fakecloud_endpoint
    apigatewayv2 = local.fakecloud_endpoint
    cloudwatch   = local.fakecloud_endpoint
    dynamodb     = local.fakecloud_endpoint
    events       = local.fakecloud_endpoint
    iam          = local.fakecloud_endpoint
    lambda       = local.fakecloud_endpoint
    logs         = local.fakecloud_endpoint
    ses          = local.fakecloud_endpoint
    sns          = local.fakecloud_endpoint
    sts          = local.fakecloud_endpoint
  }
}
