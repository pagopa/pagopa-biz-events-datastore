###############
##  API FdR  ##
###############
locals {
  apim_biz_event_datastore_service_api = {
    display_name          = "Biz-Events Datastore Helpdesk API"
    description           = "API for helpdesk on biz events datastore"
    path                  = "bizevents/datastore/helpdesk"
    subscription_required = true
    service_url           = null
  }
}

##################
##  API FdR PSP ##
##################

resource "azurerm_api_management_api_version_set" "api_biz_event_datastore_api" {
  name                = "${var.env_short}-biz-event-datastore-service-api"
  resource_group_name = local.apim.rg
  api_management_name = local.apim.name
  display_name        = local.apim_biz_event_datastore_service_api.display_name
  versioning_scheme   = "Segment"
}


module "apim_api_biz_event_datastore_api_v1" {
  source = "./.terraform/modules/__v3__/api_management_api"

  name                  = "${local.project}-biz-event-datastore-service-api"
  api_management_name   = local.apim.name
  resource_group_name   = local.apim.rg
  product_ids           = [local.apim.bizevents_helpdesk_product_id]
  subscription_required = local.apim_biz_event_datastore_service_api.subscription_required
  version_set_id        = azurerm_api_management_api_version_set.api_biz_event_datastore_api.id
  api_version           = "v1"

  description  = local.apim_biz_event_datastore_service_api.description
  display_name = local.apim_biz_event_datastore_service_api.display_name
  path         = local.apim_biz_event_datastore_service_api.path
  protocols    = ["https"]
  service_url  = local.apim_biz_event_datastore_service_api.service_url

  content_format = "openapi"

  content_value = templatefile("../openapi/openapi.json", {
    host = local.apim_hostname
  })

  xml_content = templatefile("./policy/_base_policy.xml.tpl", {
    hostname = var.hostname
  })
}
