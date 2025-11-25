package com.abac.serializer

import com.abac.model.Expression
import com.abac.model.Policy
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

class PolicySerializer(
    private val json: Json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = false
        isLenient = false
    }
) {
    fun expressionToJson(expression: Expression): String {
        return json.encodeToString(expression)
    }

    fun expressionFromJson(jsonString: String): Expression {
        return json.decodeFromString(jsonString)
    }

    fun policyToJson(policy: Policy): String {
        return json.encodeToString(policy)
    }

    fun policyFromJson(jsonString: String): Policy {
        return json.decodeFromString(jsonString)
    }

    fun expressionToJsonPretty(expression: Expression): String {
        val prettyJson = Json {
            prettyPrint = true
            encodeDefaults = true
        }
        return prettyJson.encodeToString(expression)
    }

    fun policyToJsonCompact(policy: Policy): String {
        val compactJson = Json {
            prettyPrint = false
            encodeDefaults = true
        }
        return compactJson.encodeToString(policy)
    }
}
