terraform {
  required_version = ">= 1.5"

  required_providers {
    mongodbatlas = {
      source  = "mongodb/mongodbatlas"
      version = "~> 2.7"
    }
    upstash = {
      source  = "upstash/upstash"
      version = "~> 1.2"
    }
    aiven = {
      source  = "aiven/aiven"
      version = "~> 4.37"
    }
  }
}

# MONGODB_ATLAS_PUBLIC_API_KEY / MONGODB_ATLAS_PRIVATE_API_KEY 환경변수로 인증한다.
provider "mongodbatlas" {}

# AIVEN_TOKEN 환경변수로 인증한다.
provider "aiven" {}

# upstash provider는 환경변수를 지원하지 않아 변수로 명시 전달한다.
provider "upstash" {
  email   = var.upstash_email
  api_key = var.upstash_api_key
}
