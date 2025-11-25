package com.abac.cache

import com.abac.model.Policy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class PolicyCache(
    private val maxSize: Int = 1000
) {
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val accessOrder = ConcurrentHashMap<String, AtomicLong>()
    private val accessCounter = AtomicLong(0)

    fun get(policyId: String): Policy? {
        val entry = cache[policyId] ?: return null

        accessOrder[policyId]?.set(accessCounter.incrementAndGet())

        entry.hits.incrementAndGet()
        entry.lastAccessTime = System.currentTimeMillis()

        return entry.policy
    }

    fun put(policyId: String, policy: Policy) {
        if (cache.size >= maxSize && policyId !in cache) {
            evictLeastRecentlyUsed()
        }

        val now = System.currentTimeMillis()
        val entry = CacheEntry(
            policy = policy,
            createdTime = now,
            lastAccessTime = now
        )

        cache[policyId] = entry
        accessOrder[policyId] = AtomicLong(accessCounter.incrementAndGet())
    }

    fun remove(policyId: String): Policy? {
        accessOrder.remove(policyId)
        return cache.remove(policyId)?.policy
    }

    fun clear() {
        cache.clear()
        accessOrder.clear()
    }

    fun getStatistics(): CacheStatistics {
        val totalHits = cache.values.sumOf { it.hits.get() }
        val totalSize = cache.size

        return CacheStatistics(
            size = totalSize,
            maxSize = maxSize,
            totalHits = totalHits,
            entries = cache.map { (id, entry) ->
                CacheEntryStats(
                    policyId = id,
                    hits = entry.hits.get(),
                    createdTime = entry.createdTime,
                    lastAccessTime = entry.lastAccessTime
                )
            }.sortedByDescending { it.hits }
        )
    }

    private fun evictLeastRecentlyUsed() {
        val lruPolicyId = accessOrder.entries
            .minByOrNull { it.value.get() }
            ?.key
            ?: return

        cache.remove(lruPolicyId)
        accessOrder.remove(lruPolicyId)
    }

    private data class CacheEntry(
        val policy: Policy,
        val createdTime: Long,
        var lastAccessTime: Long,
        val hits: AtomicLong = AtomicLong(0)
    )
}

data class CacheStatistics(
    val size: Int,
    val maxSize: Int,
    val totalHits: Long,
    val entries: List<CacheEntryStats>
) {
    val hitRate: Double
        get() = if (totalHits > 0) totalHits.toDouble() / size else 0.0
}

data class CacheEntryStats(
    val policyId: String,
    val hits: Long,
    val createdTime: Long,
    val lastAccessTime: Long
)
