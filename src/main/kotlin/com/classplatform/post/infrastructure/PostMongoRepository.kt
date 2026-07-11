package com.classplatform.post.infrastructure

import org.springframework.data.mongodb.repository.MongoRepository

interface PostMongoRepository : MongoRepository<PostMongoDocument, String>
