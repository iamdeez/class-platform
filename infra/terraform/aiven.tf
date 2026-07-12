resource "aiven_mysql" "class_platform_mysql" {
  project      = "deezcreator-b418"
  service_name = "class-platform-mysql"
  plan         = "free-1-1gb"
  cloud_name   = "do-blr"
}
