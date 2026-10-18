mock_provider "aws" {}
mock_provider "aws" { alias = "global" }

variables {
  static_response_headers_policy_id = "00000000-0000-0000-0000-000000000000"
  aws_account_id                    = "123456789012"
  alb_arn                           = "arn:aws:elasticloadbalancing:ap-southeast-1:123456789012:loadbalancer/app/carelink-api/0123456789abcdef"
  alb_dns_name                      = "internal-carelink-api-123.ap-southeast-1.elb.amazonaws.com"
}

run "private_site_plan" {
  command = plan
  assert {
    condition = (
      aws_cloudfront_distribution.site.default_cache_behavior[0].response_headers_policy_id == var.static_response_headers_policy_id &&
      alltrue([for behavior in aws_cloudfront_distribution.site.ordered_cache_behavior :
        behavior.response_headers_policy_id == var.static_response_headers_policy_id if behavior.path_pattern == "/assets/*"
      ])
    )
    error_message = "Both static behaviors must keep the supplied reviewed response headers."
  }
  assert {
    condition     = aws_s3_bucket_public_access_block.frontend.block_public_policy && aws_s3_bucket_public_access_block.frontend.restrict_public_buckets
    error_message = "The frontend bucket must remain private."
  }
  assert {
    condition     = aws_cloudfront_origin_access_control.frontend.signing_behavior == "always"
    error_message = "Only signed CloudFront reads may reach the static origin."
  }
  assert {
    condition     = aws_cloudfront_vpc_origin.api.vpc_origin_endpoint_config[0].origin_protocol_policy == "http-only"
    error_message = "Declare the actual private-origin protocol."
  }
  assert {
    condition     = length(aws_cloudfront_distribution.site.ordered_cache_behavior) == 3 && alltrue([for behavior in aws_cloudfront_distribution.site.ordered_cache_behavior : behavior.target_origin_id == "api" && behavior.viewer_protocol_policy == "https-only" if startswith(behavior.path_pattern, "/api")])
    error_message = "Both /api and /api/* must use the API origin over viewer HTTPS."
  }
  assert {
    condition     = alltrue([for error in aws_cloudfront_distribution.site.custom_error_response : error.error_caching_min_ttl == 0 && error.response_page_path == null])
    error_message = "API errors must not be cached or replaced by the SPA shell."
  }
}

run "reject_missing_reviewed_header_policy" {
  command = plan
  variables { static_response_headers_policy_id = "" }
  expect_failures = [var.static_response_headers_policy_id]
}

run "waf_plan" {
  command = plan
  variables { enable_waf = true }
  assert {
    condition     = length(aws_wafv2_web_acl.site) == 1 && aws_wafv2_web_acl.site[0].scope == "CLOUDFRONT" && length(aws_wafv2_web_acl.site[0].rule) == 2
    error_message = "The reviewed WAF must contain the managed threat group and rate rule."
  }
}
