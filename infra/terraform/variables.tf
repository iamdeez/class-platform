variable "upstash_email" {
  description = "Upstash 계정 이메일 (upstash provider가 환경변수를 지원하지 않아 명시 전달)"
  type        = string
  sensitive   = true
}

variable "upstash_api_key" {
  description = "Upstash Management API Key"
  type        = string
  sensitive   = true
}

variable "atlas_project_id" {
  description = "MongoDB Atlas Project ID (Project 0)"
  type        = string
}
