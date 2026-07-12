resource "mongodbatlas_advanced_cluster" "cluster0" {
  project_id   = var.atlas_project_id
  name         = "Cluster0"
  cluster_type = "REPLICASET"

  replication_specs = [
    {
      region_configs = [
        {
          priority              = 7
          provider_name         = "TENANT"
          backing_provider_name = "AWS"
          region_name           = "AP_NORTHEAST_2"
          electable_specs = {
            instance_size = "M0"
          }
        }
      ]
    }
  ]
}

resource "mongodbatlas_database_user" "app_user" {
  project_id         = var.atlas_project_id
  username           = "deezcreatordev_db_user"
  auth_database_name = "admin"
  # Atlas API가 실제 값을 반환하지 않아 import 후 항상 diff를 유발하므로 임의값 사용(아래 lifecycle로 무시).
  password = "TERRAFORM_IMPORT_PLACEHOLDER_DO_NOT_USE"

  roles {
    role_name     = "atlasAdmin"
    database_name = "admin"
  }

  # Atlas API는 실제 비밀번호를 반환하지 않아 항상 diff가 발생하므로 비교 대상에서 제외한다.
  lifecycle {
    ignore_changes = [password]
  }
}

resource "mongodbatlas_project_ip_access_list" "render_open" {
  project_id = var.atlas_project_id
  cidr_block = "0.0.0.0/0"
  comment    = "Render는 고정 IP를 제공하지 않아 전체 허용(005)"
}
