package com.abac.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive


@Serializable
sealed class Expression {

    @Serializable
    @SerialName("Constant")
    data class Constant(val value: JsonElement) : Expression() {
        constructor(value: String) : this(JsonPrimitive(value))
        constructor(value: Number) : this(JsonPrimitive(value))
        constructor(value: Boolean) : this(JsonPrimitive(value))
    }

    @Serializable
    @SerialName("Variable")
    data class Variable(val path: String) : Expression()

    @Serializable
    @SerialName("BinaryOp")
    data class BinaryOperation(
        val left: Expression,
        val operator: Operator,
        val right: Expression
    ) : Expression()

    @Serializable
    @SerialName("UnaryOp")
    data class UnaryOperation(
        val operator: UnaryOperator,
        val operand: Expression
    ) : Expression()

    @Serializable
    @SerialName("FunctionCall")
    data class FunctionCall(
        val name: String,
        val arguments: List<Expression>
    ) : Expression()

    @Serializable
    @SerialName("ListLiteral")
    data class ListLiteral(val elements: List<Expression>) : Expression()

    @Serializable
    @SerialName("WhenExpression")
    data class WhenExpression(
        val subject: Expression,
        val cases: List<WhenCase>,
        val elseCase: Expression? = null
    ) : Expression()

    @Serializable
    data class WhenCase(
        val condition: Expression,
        val result: Expression
    )
}

@Serializable
enum class Operator {
    EQUALS,
    NOT_EQUALS,
    GREATER_THAN,
    GREATER_THAN_OR_EQUAL,
    LESS_THAN,
    LESS_THAN_OR_EQUAL,

    AND,
    OR,
    IMPLIES,

    IN,
    NOT_IN,
    CONTAINS,
    NOT_CONTAINS,

    STARTS_WITH,
    ENDS_WITH,
    MATCHES,

    BETWEEN
}

@Serializable
enum class UnaryOperator {
    NOT,
    IS_NULL,
    IS_NOT_NULL
}

@Serializable
data class Policy(
    val id: String,
    val name: String,
    val expression: Expression,
    val description: String? = null,
    val version: Int = 1,
    val metadata: Map<String, String> = emptyMap()
)
