package com.abac.dsl

import com.abac.model.*
import kotlinx.serialization.json.JsonPrimitive

@DslMarker
annotation class PolicyDsl

fun policy(id: String, name: String, block: ExpressionBuilder.() -> Expression): Policy {
    return Policy(
        id = id,
        name = name,
        expression = ExpressionBuilder().block()
    )
}

fun policy(name: String, block: ExpressionBuilder.() -> Expression): Policy {
    return Policy(
        id = name.lowercase().replace(" ", "_"),
        name = name,
        expression = ExpressionBuilder().block()
    )
}

@PolicyDsl
class ExpressionBuilder {
    fun subject(path: String) = Expression.Variable("subject.$path")

    val subject: SubjectBuilder get() = SubjectBuilder()

    fun resource(path: String) = Expression.Variable("resource.$path")

    val resource: ResourceBuilder get() = ResourceBuilder()

    fun action() = Expression.Variable("action")

    fun environment(path: String) = Expression.Variable("environment.$path")

    val user: SubjectBuilder get() = SubjectBuilder()

    fun const(value: Any): Expression = when (value) {
        is String -> Expression.Constant(JsonPrimitive(value))
        is Number -> Expression.Constant(JsonPrimitive(value))
        is Boolean -> Expression.Constant(JsonPrimitive(value))
        is List<*> -> Expression.ListLiteral(value.map { const(it!!) })
        else -> throw IllegalArgumentException("Unsupported constant type: ${value::class}")
    }

    fun listOf(vararg elements: Any): Expression {
        return Expression.ListLiteral(elements.map { const(it) })
    }

    infix fun Expression.eq(other: Expression) =
        Expression.BinaryOperation(this, Operator.EQUALS, other)

    infix fun Expression.eq(value: Any) =
        this eq const(value)

    infix fun Expression.neq(other: Expression) =
        Expression.BinaryOperation(this, Operator.NOT_EQUALS, other)

    infix fun Expression.neq(value: Any) =
        this neq const(value)

    infix fun Expression.gt(other: Expression) =
        Expression.BinaryOperation(this, Operator.GREATER_THAN, other)

    infix fun Expression.gt(value: Number) =
        this gt const(value)

    infix fun Expression.gte(other: Expression) =
        Expression.BinaryOperation(this, Operator.GREATER_THAN_OR_EQUAL, other)

    infix fun Expression.gte(value: Number) =
        this gte const(value)

    infix fun Expression.lt(other: Expression) =
        Expression.BinaryOperation(this, Operator.LESS_THAN, other)

    infix fun Expression.lt(value: Number) =
        this lt const(value)

    infix fun Expression.lte(other: Expression) =
        Expression.BinaryOperation(this, Operator.LESS_THAN_OR_EQUAL, other)

    infix fun Expression.lte(value: Number) =
        this lte const(value)

    infix fun Expression.and(other: Expression) =
        Expression.BinaryOperation(this, Operator.AND, other)

    infix fun Expression.or(other: Expression) =
        Expression.BinaryOperation(this, Operator.OR, other)

    fun not(expression: Expression) =
        Expression.UnaryOperation(UnaryOperator.NOT, expression)

    /**
     * A implies B = (NOT A) OR B
     */
    infix fun Expression.implies(other: Expression) =
        Expression.BinaryOperation(this, Operator.IMPLIES, other)

    infix fun Expression.isIn(collection: Expression) =
        Expression.BinaryOperation(this, Operator.IN, collection)

    infix fun Expression.isIn(collection: List<Any>) =
        this isIn const(collection)

    infix fun Expression.notIn(collection: Expression) =
        Expression.BinaryOperation(this, Operator.NOT_IN, collection)

    infix fun Expression.notIn(collection: List<Any>) =
        this notIn const(collection)

    infix fun Expression.contains(element: Expression) =
        Expression.BinaryOperation(this, Operator.CONTAINS, element)

    infix fun Expression.contains(element: Any) =
        this contains const(element)

    infix fun Expression.notContains(element: Expression) =
        Expression.BinaryOperation(this, Operator.NOT_CONTAINS, element)

    infix fun Expression.notContains(element: Any) =
        this notContains const(element)

    infix fun Expression.startsWith(prefix: Expression) =
        Expression.BinaryOperation(this, Operator.STARTS_WITH, prefix)

    infix fun Expression.startsWith(prefix: String) =
        this startsWith const(prefix)

    infix fun Expression.endsWith(suffix: Expression) =
        Expression.BinaryOperation(this, Operator.ENDS_WITH, suffix)

    infix fun Expression.endsWith(suffix: String) =
        this endsWith const(suffix)

    infix fun Expression.matches(pattern: Expression) =
        Expression.BinaryOperation(this, Operator.MATCHES, pattern)

    infix fun Expression.matches(pattern: String) =
        this matches const(pattern)

    fun Expression.isNull() =
        Expression.UnaryOperation(UnaryOperator.IS_NULL, this)

    fun Expression.isNotNull() =
        Expression.UnaryOperation(UnaryOperator.IS_NOT_NULL, this)

    fun currentTime() = Expression.FunctionCall("currentTime", emptyList())

    fun between(value: Expression, start: Expression, end: Expression) =
        Expression.FunctionCall("between", listOf(value, start, end))

    fun hasRole(role: String) =
        Expression.FunctionCall("hasRole", listOf(const(role)))

    fun hasAnyRole(vararg roles: String) =
        Expression.FunctionCall("hasAnyRole", roles.map { const(it) })

    fun hasAllRoles(vararg roles: String) =
        Expression.FunctionCall("hasAllRoles", roles.map { const(it) })

    fun whenCase(subject: Expression, block: WhenBuilder.() -> Unit): Expression {
        val builder = WhenBuilder()
        builder.block()
        return Expression.WhenExpression(
            subject = subject,
            cases = builder.cases,
            elseCase = builder.elseCase
        )
    }

    @PolicyDsl
    class WhenBuilder {
        internal val cases = mutableListOf<Expression.WhenCase>()
        internal var elseCase: Expression? = null

        infix fun Expression.then(result: Expression) {
            cases.add(Expression.WhenCase(this, result))
        }

        infix fun Expression.then(result: Boolean) {
            this then const(result)
        }

        infix fun Expression.then(result: String) {
            this then const(result)
        }

        fun elseCase(result: Expression) {
            elseCase = result
        }

        fun elseCase(result: Boolean) {
            elseCase = const(result)
        }
    }
}

@PolicyDsl
class SubjectBuilder {
    val id get() = Expression.Variable("subject.id")
    val role get() = Expression.Variable("subject.role")
    val roles get() = Expression.Variable("subject.roles")
    val department get() = Expression.Variable("subject.department")
    val departments get() = Expression.Variable("subject.departments")
    val riskScore get() = Expression.Variable("subject.riskScore")
    val assignedClients get() = Expression.Variable("subject.assignedClients")
    val subLevel get() = Expression.Variable("subject.subLevel")
    val dailyUsage get() = Expression.Variable("subject.dailyUsage")

    operator fun get(key: String) = Expression.Variable("subject.$key")
}

@PolicyDsl
class ResourceBuilder {
    val id get() = Expression.Variable("resource.id")
    val type get() = Expression.Variable("resource.type")
    val owner get() = ResourceOwnerBuilder()
    val ownerId get() = Expression.Variable("resource.ownerId")
    val department get() = Expression.Variable("resource.department")
    val status get() = Expression.Variable("resource.status")
    val client get() = Expression.Variable("resource.client")
    val creatorId get() = Expression.Variable("resource.creatorId")
    val delegatedTo get() = Expression.Variable("resource.delegatedTo")

    operator fun get(key: String) = Expression.Variable("resource.$key")
}

@PolicyDsl
class ResourceOwnerBuilder {
    val id get() = Expression.Variable("resource.owner.id")
    val managerId get() = Expression.Variable("resource.owner.managerId")
    val department get() = Expression.Variable("resource.owner.department")

    operator fun get(key: String) = Expression.Variable("resource.owner.$key")
}
