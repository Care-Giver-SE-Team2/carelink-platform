terraform {
  required_version = ">= 1.11, < 2.0"
  required_providers {
    aws = { source = "hashicorp/aws", version = "~> 6.0" }
  }
  # Configure a separate key with the reviewed local backend file.
  backend "s3" {}
}

provider "aws" {
  region              = "ap-southeast-1"
  allowed_account_ids = [var.aws_account_id]
  default_tags {
    tags = { Project = "CareLink", ManagedBy = "Terraform", Component = "PublicSite" }
  }
}

provider "aws" {
  alias               = "global"
  region              = "us-east-1"
  allowed_account_ids = [var.aws_account_id]
  default_tags {
    tags = { Project = "CareLink", ManagedBy = "Terraform", Component = "PublicSite" }
  }
}

variable "aws_account_id" {
  type = string
  validation {
    condition     = can(regex("^[0-9]{12}$", var.aws_account_id))
    error_message = "Specify the reviewed AWS account ID."
  }
}

variable "static_response_headers_policy_id" {
  description = "Existing reviewed static response-header policy. This root does not create it; copy its ID only from an authorized read of the selected account."
  type        = string

  validation {
    condition     = can(regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$", var.static_response_headers_policy_id))
    error_message = "Supply the reviewed response-header policy UUID; do not silently remove CSP/security headers."
  }
}

variable "alb_arn" {
  description = "The active internal ALB created by the reviewed EKS Ingress."
  type        = string
}

variable "alb_dns_name" {
  description = "The internal ALB DNS name, without scheme or path."
  type        = string
}

variable "waf_web_acl_arn" {
  description = "Optional existing CloudFront-scope WAF ACL ARN; empty means WAF is not enabled."
  type        = string
  default     = ""
}

variable "enable_waf" {
  description = "Create a reviewed CloudFront-scope WAF when no existing ACL is supplied."
  type        = bool
  default     = false
}

resource "aws_s3_bucket" "frontend" {
  bucket = "carelink-frontend-${var.aws_account_id}-staging"
  # Static build files are reproducible; the reviewed preview cleanup deletes versions too.
  force_destroy = true
}

resource "aws_s3_bucket_public_access_block" "frontend" {
  bucket                  = aws_s3_bucket.frontend.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "frontend" {
  bucket = aws_s3_bucket.frontend.id
  rule { object_ownership = "BucketOwnerEnforced" }
}

resource "aws_s3_bucket_versioning" "frontend" {
  bucket = aws_s3_bucket.frontend.id
  versioning_configuration { status = "Enabled" }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "frontend" {
  bucket = aws_s3_bucket.frontend.id
  rule {
    apply_server_side_encryption_by_default { sse_algorithm = "AES256" }
  }
}

resource "aws_cloudfront_origin_access_control" "frontend" {
  name                              = "carelink-staging-frontend"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

resource "aws_cloudfront_vpc_origin" "api" {
  vpc_origin_endpoint_config {
    name       = "carelink-staging-api"
    arn        = var.alb_arn
    http_port  = 80
    https_port = 443
    # HTTPS for viewers; this hop uses a private VPC connection over HTTP.
    origin_protocol_policy = "http-only"
    origin_ssl_protocols {
      items    = ["TLSv1.2"]
      quantity = 1
    }
  }
}

resource "aws_cloudfront_function" "spa" {
  name    = "carelink-staging-spa"
  runtime = "cloudfront-js-2.0"
  publish = true
  code    = file("${path.module}/../../deploy/cloudfront/spa.js")
}

data "aws_cloudfront_cache_policy" "api" { name = "Managed-CachingDisabled" }
data "aws_cloudfront_cache_policy" "assets" { name = "Managed-CachingOptimized" }
data "aws_cloudfront_origin_request_policy" "api" { name = "Managed-AllViewer" }

resource "aws_cloudfront_distribution" "site" {
  enabled             = true
  is_ipv6_enabled     = true
  default_root_object = "index.html"
  price_class         = "PriceClass_200"
  web_acl_id          = var.enable_waf ? aws_wafv2_web_acl.site[0].arn : (var.waf_web_acl_arn == "" ? null : var.waf_web_acl_arn)
  wait_for_deployment = true

  origin {
    origin_id                = "frontend"
    domain_name              = aws_s3_bucket.frontend.bucket_regional_domain_name
    origin_access_control_id = aws_cloudfront_origin_access_control.frontend.id
  }
  origin {
    origin_id   = "api"
    domain_name = var.alb_dns_name
    vpc_origin_config { vpc_origin_id = aws_cloudfront_vpc_origin.api.id }
    # Replace a viewer-supplied Forwarded header at the trusted edge.
    custom_header {
      name  = "Forwarded"
      value = "proto=https"
    }
  }

  default_cache_behavior {
    target_origin_id       = "frontend"
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    viewer_protocol_policy = "redirect-to-https"
    # Never cache index.html or SPA routes; hashed assets have a separate behavior.
    cache_policy_id = data.aws_cloudfront_cache_policy.api.id
    compress        = true

    # Reviewed static frontend response headers; API behaviors remain separate.
    response_headers_policy_id = var.static_response_headers_policy_id

    function_association {
      event_type   = "viewer-request"
      function_arn = aws_cloudfront_function.spa.arn
    }
  }

  dynamic "ordered_cache_behavior" {
    for_each = ["/api", "/api/*"]
    content {
      path_pattern             = ordered_cache_behavior.value
      target_origin_id         = "api"
      allowed_methods          = ["GET", "HEAD", "OPTIONS", "POST", "PUT", "PATCH", "DELETE"]
      cached_methods           = ["GET", "HEAD"]
      viewer_protocol_policy   = "https-only"
      cache_policy_id          = data.aws_cloudfront_cache_policy.api.id
      origin_request_policy_id = data.aws_cloudfront_origin_request_policy.api.id
    }
  }
  ordered_cache_behavior {
    path_pattern           = "/assets/*"
    target_origin_id       = "frontend"
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    viewer_protocol_policy = "redirect-to-https"
    cache_policy_id        = data.aws_cloudfront_cache_policy.assets.id
    compress               = true

    # Reviewed static frontend response headers; API behaviors remain separate.
    response_headers_policy_id = var.static_response_headers_policy_id
  }
  # Do not turn API authentication errors into an index.html response.
  dynamic "custom_error_response" {
    for_each = [403, 404, 500, 502, 503, 504]
    content {
      error_code            = custom_error_response.value
      error_caching_min_ttl = 0
    }
  }
  restrictions {
    geo_restriction { restriction_type = "none" }
  }
  viewer_certificate { cloudfront_default_certificate = true }
}

resource "aws_s3_bucket_policy" "frontend" {
  bucket = aws_s3_bucket.frontend.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow", Principal = { Service = "cloudfront.amazonaws.com" }
      Action    = "s3:GetObject", Resource = "${aws_s3_bucket.frontend.arn}/*"
      Condition = { StringEquals = { "AWS:SourceArn" = aws_cloudfront_distribution.site.arn } }
    }]
  })
}

resource "aws_iam_role" "frontend_deploy" {
  name                 = "CareLinkGitHubFrontendDeploy"
  permissions_boundary = "arn:aws:iam::${var.aws_account_id}:policy/CareLinkFrontendDeployBoundary"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow", Action = "sts:AssumeRoleWithWebIdentity"
      Principal = { Federated = "arn:aws:iam::${var.aws_account_id}:oidc-provider/token.actions.githubusercontent.com" }
      Condition = { StringEquals = {
        "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
        "token.actions.githubusercontent.com:sub" = "repo:Care-Giver-SE-Team2@323581092/carelink-platform@1408273257:ref:refs/heads/main"
      } }
    }]
  })
}

resource "aws_iam_role_policy" "frontend_deploy" {
  name = "PublishCareLinkStaticFrontend"
  role = aws_iam_role.frontend_deploy.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      { Effect = "Allow", Action = "s3:PutObject", Resource = "${aws_s3_bucket.frontend.arn}/*" },
      { Effect = "Allow", Action = ["cloudfront:GetDistribution", "cloudfront:CreateInvalidation", "cloudfront:GetInvalidation"], Resource = aws_cloudfront_distribution.site.arn }
    ]
  })
}

output "frontend_bucket" { value = aws_s3_bucket.frontend.id }
output "distribution_id" { value = aws_cloudfront_distribution.site.id }
output "public_url" { value = "https://${aws_cloudfront_distribution.site.domain_name}" }
output "frontend_deploy_role_arn" { value = aws_iam_role.frontend_deploy.arn }
