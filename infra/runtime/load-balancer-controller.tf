variable "enable_public_ingress" {
  description = "Prepare the private ALB controller identity for the reviewed public preview."
  type        = bool
  default     = false
}

resource "aws_iam_role" "load_balancer_controller" {
  count                = var.enable_public_ingress ? 1 : 0
  name                 = "CareLinkRuntimeLoadBalancerController"
  permissions_boundary = "arn:aws:iam::${var.aws_account_id}:policy/CareLinkLoadBalancerBoundary"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow", Action = ["sts:AssumeRole", "sts:TagSession"]
      Principal = { Service = "pods.eks.amazonaws.com" }
      Condition = { StringEquals = {
        "aws:RequestTag/eks-cluster-arn"            = aws_eks_cluster.runtime.arn
        "aws:RequestTag/kubernetes-namespace"       = "kube-system"
        "aws:RequestTag/kubernetes-service-account" = "aws-load-balancer-controller"
      } }
    }]
  })
}

resource "aws_iam_role_policy" "load_balancer_controller" {
  count  = var.enable_public_ingress ? 1 : 0
  name   = "ManageCareLinkPrivateAlb"
  role   = aws_iam_role.load_balancer_controller[0].id
  policy = templatefile("${path.module}/controller-policy.json", { aws_account_id = var.aws_account_id })
}

resource "aws_eks_pod_identity_association" "load_balancer_controller" {
  count           = var.enable_public_ingress ? 1 : 0
  cluster_name    = aws_eks_cluster.runtime.name
  namespace       = "kube-system"
  service_account = "aws-load-balancer-controller"
  role_arn        = aws_iam_role.load_balancer_controller[0].arn
  depends_on      = [aws_iam_role_policy.load_balancer_controller, aws_eks_addon.pod_identity]
}
