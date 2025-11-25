package com.abac.model


sealed class AccessDecision {
    data class Decision(
        val allowed: Boolean,
        val policyId: String,
        val policyName: String,
        val evaluationTimeMs: Long = 0
    ) : AccessDecision()

    data class Error(
        val message: String,
        val cause: Throwable? = null

    data class Timeout(
        val policyId: String,
        val timeoutMs: Long
    ) : AccessDecision()
}

class EvaluationException(message: String, cause: Throwable? = null) : Exception(message, cause)

class ValidationException(message: String, cause: Throwable? = null) : Exception(message, cause)
