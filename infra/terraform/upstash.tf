resource "upstash_redis_database" "class_platform_redis" {
  database_name  = "Flowrit"
  region         = "global"
  primary_region = "ap-northeast-1"
}
