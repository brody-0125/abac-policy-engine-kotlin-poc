package com.abac

import com.abac.cache.PolicyCache
import com.abac.evaluator.FunctionRegistry
import com.abac.evaluator.PolicyEvaluator
import com.abac.model.*
import com.abac.serializer.PolicySerializer
import com.abac.validator.PolicyValidator
import com.abac.validator.ValidationResult
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class AbacEngine(
    private val policyRepository: PolicyRepository? = null,
    private val evaluator: PolicyEvaluator = PolicyEvaluator(),
    private val validator: PolicyValidator = PolicyValidator(),
    private val serializer: PolicySerializer = PolicySerializer(),
    private val cache: PolicyCache = PolicyCache(),
    private val config: EngineConfig = EngineConfig()
) {
    data class EngineConfig(
        val evaluationTimeout: Duration = 100.milliseconds,
        val enableCache: Boolean = true,
        val validateBeforeEvaluation: Boolean = true,
        val throwOnValidationError: Boolean = true
    )

    suspend fun checkAccess(
        policyId: String,
        context: EvaluationContext
    ): AccessDecision {
        val startTime = System.currentTimeMillis()

        return try {
            val policy = loadPolicy(policyId)

            if (config.validateBeforeEvaluation) {
                when (val validationResult = validator.validate(policy.expression)) {
                    is ValidationResult.Invalid -> {
                        if (config.throwOnValidationError) {
                            return AccessDecision.Error(
                                message = "Policy validation failed: ${validationResult.issues.joinToString()}",
                                cause = ValidationException(validationResult.issues.toString())
                            )
                        }
                    }
                    ValidationResult.Valid -> { }
                }
            }

            val allowed = withTimeout(config.evaluationTimeout) {
                evaluator.evaluate(policy.expression, context)
            }

            val evaluationTime = System.currentTimeMillis() - startTime

            AccessDecision.Decision(
                allowed = allowed,
                policyId = policy.id,
                policyName = policy.name,
                evaluationTimeMs = evaluationTime
            )

        } catch (e: TimeoutCancellationException) {
            AccessDecision.Timeout(
                policyId = policyId,
                timeoutMs = config.evaluationTimeout.inWholeMilliseconds
            )
        } catch (e: EvaluationException) {
            AccessDecision.Error(
                message = "Evaluation error: ${e.message}",
                cause = e
            )
        } catch (e: Exception) {
            AccessDecision.Error(
                message = "Unexpected error: ${e.message}",
                cause = e
            )
        }
    }

    suspend fun checkAccessDirect(
        policyName: String,
        expression: Expression,
        context: EvaluationContext
    ): AccessDecision {
        val startTime = System.currentTimeMillis()

        return try {
            if (config.validateBeforeEvaluation) {
                when (val validationResult = validator.validate(expression)) {
                    is ValidationResult.Invalid -> {
                        if (config.throwOnValidationError) {
                            return AccessDecision.Error(
                                message = "Policy validation failed: ${validationResult.issues.joinToString()}",
                                cause = ValidationException(validationResult.issues.toString())
                            )
                        }
                    }
                    ValidationResult.Valid -> { }
                }
            }

            val allowed = withTimeout(config.evaluationTimeout) {
                evaluator.evaluate(expression, context)
            }

            val evaluationTime = System.currentTimeMillis() - startTime

            AccessDecision.Decision(
                allowed = allowed,
                policyId = "direct",
                policyName = policyName,
                evaluationTimeMs = evaluationTime
            )

        } catch (e: TimeoutCancellationException) {
            AccessDecision.Timeout(
                policyId = "direct",
                timeoutMs = config.evaluationTimeout.inWholeMilliseconds
            )
        } catch (e: EvaluationException) {
            AccessDecision.Error(
                message = "Evaluation error: ${e.message}",
                cause = e
            )
        } catch (e: Exception) {
            AccessDecision.Error(
                message = "Unexpected error: ${e.message}",
                cause = e
            )
        }
    }

    suspend fun checkAccessAll(
        policyIds: List<String>,
        context: EvaluationContext
    ): Map<String, AccessDecision> {
        val results = mutableMapOf<String, AccessDecision>()

        for (policyId in policyIds) {
            val decision = checkAccess(policyId, context)
            results[policyId] = decision

            when (decision) {
                is AccessDecision.Decision -> if (!decision.allowed) return results
                is AccessDecision.Error, is AccessDecision.Timeout -> return results
            }
        }

        return results
    }

    suspend fun checkAccessAny(
        policyIds: List<String>,
        context: EvaluationContext
    ): Map<String, AccessDecision> {
        val results = mutableMapOf<String, AccessDecision>()

        for (policyId in policyIds) {
            val decision = checkAccess(policyId, context)
            results[policyId] = decision

            if (decision is AccessDecision.Decision && decision.allowed) {
                return results
            }
        }

        return results
    }

    fun storePolicy(policy: Policy): String {
        val validationResult = validator.validate(policy.expression)
        if (validationResult is ValidationResult.Invalid) {
            throw ValidationException(
                "Cannot store invalid policy: ${validationResult.issues.joinToString()}"
            )
        }

        val json = serializer.policyToJson(policy)
        policyRepository?.savePolicy(policy.id, json)

        if (config.enableCache) {
            cache.put(policy.id, policy)
        }

        return json
    }

    private suspend fun loadPolicy(policyId: String): Policy {
        if (config.enableCache) {
            cache.get(policyId)?.let { return it }
        }

        val repository = policyRepository
            ?: throw IllegalStateException("No policy repository configured")

        val json = repository.fetchPolicyJson(policyId)
        val policy = serializer.policyFromJson(json)

        if (config.enableCache) {
            cache.put(policyId, policy)
        }

        return policy
    }

    fun invalidateCache(policyId: String) {
        cache.remove(policyId)
    }

    fun clearCache() {
        cache.clear()
    }

    fun getCacheStatistics() = cache.getStatistics()

    fun validatePolicy(policy: Policy): ValidationResult {
        return validator.validate(policy.expression)
    }

    fun serializePolicy(policy: Policy): String {
        return serializer.policyToJson(policy)
    }

    fun deserializePolicy(json: String): Policy {
        return serializer.policyFromJson(json)
    }
}


interface PolicyRepository {
    suspend fun fetchPolicyJson(policyId: String): String

    fun savePolicy(policyId: String, json: String)

    fun deletePolicy(policyId: String)

    suspend fun listPolicyIds(): List<String>
}

class InMemoryPolicyRepository : PolicyRepository {
    private val policies = mutableMapOf<String, String>()

    override suspend fun fetchPolicyJson(policyId: String): String {
        return policies[policyId]
            ?: throw IllegalArgumentException("Policy not found: $policyId")
    }

    override fun savePolicy(policyId: String, json: String) {
        policies[policyId] = json
    }

    override fun deletePolicy(policyId: String) {
        policies.remove(policyId)
    }

    override suspend fun listPolicyIds(): List<String> {
        return policies.keys.toList()
    }

    fun clear() {
        policies.clear()
    }
}
