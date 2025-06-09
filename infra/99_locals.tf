locals {
  product = "${var.prefix}-${var.env_short}"
  project = "${var.prefix}-${var.env_short}-${var.location_short}-${var.domain}"

  apim = {
    name           = "${local.product}-apim"
    rg             = "${local.product}-api-rg"
    bizevents_helpdesk_product_id = "bizevents-helpdesk"
  }

  apim_hostname = "api.${var.apim_dns_zone_prefix}.${var.external_domain}"
}
