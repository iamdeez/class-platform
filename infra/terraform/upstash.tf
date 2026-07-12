resource "upstash_redis_database" "class_platform_redis" {
  database_name  = "Flowrit"
  region         = "global"
  primary_region = "ap-northeast-1"
  # tls 변경은 리소스 재생성을 유발하므로 실제 값(true, Upstash 기본 TLS 활성)에 맞춘다.
  tls = true
}
