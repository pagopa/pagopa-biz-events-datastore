data "azurerm_storage_account" "tf_storage_account"{
  name                = "pagopainfraterraform${var.env}"
  resource_group_name = "io-infra-rg"
}

data "azurerm_resource_group" "dashboards" {
  name = "dashboards"
}

data "azurerm_kubernetes_cluster" "aks" {
  name                = local.aks_cluster.name
  resource_group_name = local.aks_cluster.resource_group_name
}

data "github_organization_teams" "all" {
  root_teams_only = true
  summary_only    = true
}

data "azurerm_key_vault" "key_vault" {
  name                = "pagopa-${var.env_short}-kv"
  resource_group_name = "pagopa-${var.env_short}-sec-rg"
}


data "azurerm_user_assigned_identity" "identity_cd_01" {
  name                = "${local.prefix}-${var.env_short}-${local.domain}-job-01-github-cd-identity"
  resource_group_name = "${local.prefix}-${var.env_short}-identity-rg"
}

data "azurerm_key_vault" "domain_key_vault" {
  name                = "pagopa-${var.env_short}-${local.domain}-kv"
  resource_group_name = "pagopa-${var.env_short}-${local.domain}-sec-rg"
}

data "azurerm_key_vault_secret" "key_vault_sonar" {
  name         = "sonar-token"
  key_vault_id = data.azurerm_key_vault.key_vault.id
}

data "azurerm_key_vault_secret" "key_vault_bot_cd_token" {
  # name         = "pagopa-platform-domain-github-bot-cd-pat"
  # key_vault_id = data.azurerm_key_vault.domain_key_vault.id
  name         = "bot-token-github"
  key_vault_id = data.azurerm_key_vault.key_vault.id
}

data "azurerm_key_vault_secret" "key_vault_cucumber_token" {
  name         = "cucumber-token"
  key_vault_id = data.azurerm_key_vault.key_vault.id
}

data "azurerm_key_vault_secret" "key_vault_integration_test_subkey" {
  name         = "integration-test-subkey"
  key_vault_id = data.azurerm_key_vault.key_vault.id
}

data "azurerm_user_assigned_identity" "workload_identity_clientid" {
  name                = "bizevents-workload-identity"
  resource_group_name = "pagopa-${var.env_short}-weu-${var.env}-aks-rg"
}

data "azurerm_key_vault_secret" "key_vault_integration_cosmos_biz_key" {
  count  = var.env_short != "p" ? 1 : 0
  name = format("cosmos-%s-biz-key", var.env_short)
  key_vault_id = data.azurerm_key_vault.domain_key_vault.id
}

data "azurerm_key_vault_secret" "key_vault_integration_ehub_tx_biz_key" {
  count  = var.env_short != "p" ? 1 : 0
  name = format("ehub-tx-%s-biz-key", var.env_short)
  key_vault_id = data.azurerm_key_vault.domain_key_vault.id
}

data "azurerm_key_vault_secret" "key_vault_integration_ehub_biz_connection_string" {
  count  = var.env_short != "p" ? 1 : 0
  name = format("ehub-%s-biz-connection-string", var.env_short)
  key_vault_id = data.azurerm_key_vault.domain_key_vault.id
}
