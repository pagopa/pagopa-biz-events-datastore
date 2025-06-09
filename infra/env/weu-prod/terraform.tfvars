prefix    = "pagopa"
env       = "prod"
env_short = "p"
domain    = "bizevents"

location_short = "weu"

tags = {
  CreatedBy   = "Terraform"
  Environment = "Prod"
  Owner       = "pagoPA"
  Source      = "https://github.com/pagopa/pagopa-biz-event-datastore"
  CostCenter  = "TS310 - PAGAMENTI & SERVIZI"
}

apim_dns_zone_prefix = "platform"
external_domain      = "pagopa.it"
hostname             = "weuprod.bizevents.internal.platform.pagopa.it"
