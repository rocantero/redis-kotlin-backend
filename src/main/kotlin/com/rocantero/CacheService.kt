package com.rocantero

import redis.clients.jedis.Jedis

class CacheService {
  private val jedis = Jedis("localhost")

  fun put(key: String, value: String) {
    jedis.set(key, value)
  }

  fun get(key: String): String? {
    return jedis.get(key)
  }

  fun delete(key: String) {
    jedis.del(key)
  }
}