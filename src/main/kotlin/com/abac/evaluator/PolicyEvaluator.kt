package com.abac.evaluator

import com.abac.model.*
import kotlinx.serialization.json.*

/**
 * Evaluates policy expressions against evaluation contexts
 */
class PolicyEvaluator(
    private val functionRegistry: FunctionRegistry = FunctionRegistry()
) {
    /**
     * Evaluate an expression and return a boolean result
     */
    fun evaluate(expression: Expression, context: EvaluationContext): Boolean {
        val result = evaluateExpression(expression, context)
        return when (result) {
            is Boolean -> result
            else -> throw EvaluationException(
                "Expression must evaluate to Boolean, got ${result::class.simpleName}: $result"
            )
        }
    }

    /**
     * Evaluate an expression and return the raw result
     */
    private fun evaluateExpression(expression: Expression, context: EvaluationContext): Any {
        return when (expression) {
            is Expression.Constant -> evaluateConstant(expression)
            is Expression.Variable -> evaluateVariable(expression, context)
            is Expression.BinaryOperation -> evaluateBinaryOperation(expression, context)
            is Expression.UnaryOperation -> evaluateUnaryOperation(expression, context)
            is Expression.FunctionCall -> evaluateFunctionCall(expression, context)
            is Expression.ListLiteral -> evaluateListLiteral(expression, context)
            is Expression.WhenExpression -> evaluateWhenExpression(expression, context)
        }
    }

    /**
     * Evaluate a constant value
     */
    private fun evaluateConstant(constant: Expression.Constant): Any {
        return when (val value = constant.value) {
            is JsonPrimitive -> when {
                value.isString -> value.content
                value.booleanOrNull != null -> value.boolean
                value.intOrNull != null -> value.int
                value.longOrNull != null -> value.long
                value.doubleOrNull != null -> value.double
                else -> value.content
            }
            else -> throw EvaluationException("Unsupported constant value type: ${value::class}")
        }
    }

    /**
     * Evaluate a variable reference
     */
    private fun evaluateVariable(variable: Expression.Variable, context: EvaluationContext): Any {
        return context.resolve(variable.path)
            ?: throw EvaluationException("Variable not found: ${variable.path}")
    }

    /**
     * Evaluate a binary operation
     */
    private fun evaluateBinaryOperation(
        operation: Expression.BinaryOperation,
        context: EvaluationContext
    ): Any {
        val left = evaluateExpression(operation.left, context)

        if (operation.operator == Operator.AND && left == false) return false
        if (operation.operator == Operator.OR && left == true) return true

        val right = evaluateExpression(operation.right, context)

        return when (operation.operator) {
            Operator.EQUALS -> left == right
            Operator.NOT_EQUALS -> left != right
            Operator.GREATER_THAN -> compareValues(left, right) > 0
            Operator.GREATER_THAN_OR_EQUAL -> compareValues(left, right) >= 0
            Operator.LESS_THAN -> compareValues(left, right) < 0
            Operator.LESS_THAN_OR_EQUAL -> compareValues(left, right) <= 0

            Operator.AND -> (left as Boolean) && (right as Boolean)
            Operator.OR -> (left as Boolean) || (right as Boolean)
            Operator.IMPLIES -> !(left as Boolean) || (right as Boolean)

            Operator.IN -> evaluateIn(left, right)
            Operator.NOT_IN -> !evaluateIn(left, right)
            Operator.CONTAINS -> evaluateContains(left, right)
            Operator.NOT_CONTAINS -> !evaluateContains(left, right)

            Operator.STARTS_WITH -> evaluateStartsWith(left, right)
            Operator.ENDS_WITH -> evaluateEndsWith(left, right)
            Operator.MATCHES -> evaluateMatches(left, right)

            Operator.BETWEEN -> throw EvaluationException(
                "BETWEEN operator should be used through between() function"
            )
        }
    }

    /**
     * Evaluate a unary operation
     */
    private fun evaluateUnaryOperation(
        operation: Expression.UnaryOperation,
        context: EvaluationContext
    ): Any {
        val operand = try {
            evaluateExpression(operation.operand, context)
        } catch (e: EvaluationException) {
            if (operation.operator == UnaryOperator.IS_NULL) return true
            if (operation.operator == UnaryOperator.IS_NOT_NULL) return false
            throw e
        }

        return when (operation.operator) {
            UnaryOperator.NOT -> !(operand as Boolean)
            UnaryOperator.IS_NULL -> operand == null
            UnaryOperator.IS_NOT_NULL -> operand != null
        }
    }

    /**
     * Evaluate a function call
     */
    private fun evaluateFunctionCall(
        call: Expression.FunctionCall,
        context: EvaluationContext
    ): Any {
        val function = functionRegistry.get(call.name)
            ?: throw EvaluationException("Function not found: ${call.name}")

        val args = call.arguments.map { evaluateExpression(it, context) }

        return function(args, context)
    }

    /**
     * Evaluate a list literal
     */
    private fun evaluateListLiteral(
        list: Expression.ListLiteral,
        context: EvaluationContext
    ): List<Any> {
        return list.elements.map { evaluateExpression(it, context) }
    }

    /**
     * Evaluate a when expression (pattern matching)
     */
    private fun evaluateWhenExpression(
        whenExpr: Expression.WhenExpression,
        context: EvaluationContext
    ): Any {
        val subjectValue = evaluateExpression(whenExpr.subject, context)

        for (case in whenExpr.cases) {
            val conditionValue = evaluateExpression(case.condition, context)

            if (subjectValue == conditionValue) {
                return evaluateExpression(case.result, context)
            }
        }

        if (whenExpr.elseCase != null) {
            return evaluateExpression(whenExpr.elseCase, context)
        }

        throw EvaluationException("No matching case in when expression for value: $subjectValue")
    }

    // ============================================
    // ============================================

    /**
     * Compare two values
     */
    private fun compareValues(left: Any, right: Any): Int {
        return when {
            left is Number && right is Number ->
                left.toDouble().compareTo(right.toDouble())

            left is String && right is String ->
                left.compareTo(right)

            left is Comparable<*> && right is Comparable<*> && left::class == right::class -> {
                @Suppress("UNCHECKED_CAST")
                (left as Comparable<Any>).compareTo(right)
            }

            else -> throw EvaluationException(
                "Cannot compare values of types ${left::class.simpleName} and ${right::class.simpleName}"
            )
        }
    }

    /**
     * Evaluate IN operator
     */
    private fun evaluateIn(element: Any, collection: Any): Boolean {
        return when (collection) {
            is Collection<*> -> element in collection
            is Array<*> -> element in collection
            is String -> element is String && element in collection
            else -> throw EvaluationException(
                "IN operator requires a collection, got ${collection::class.simpleName}"
            )
        }
    }

    /**
     * Evaluate CONTAINS operator
     */
    private fun evaluateContains(collection: Any, element: Any): Boolean {
        return when (collection) {
            is Collection<*> -> element in collection
            is Array<*> -> element in collection
            is String -> element is String && element in collection
            else -> throw EvaluationException(
                "CONTAINS operator requires a collection, got ${collection::class.simpleName}"
            )
        }
    }

    /**
     * Evaluate STARTS_WITH operator
     */
    private fun evaluateStartsWith(str: Any, prefix: Any): Boolean {
        if (str !is String || prefix !is String) {
            throw EvaluationException("STARTS_WITH requires string operands")
        }
        return str.startsWith(prefix)
    }

    /**
     * Evaluate ENDS_WITH operator
     */
    private fun evaluateEndsWith(str: Any, suffix: Any): Boolean {
        if (str !is String || suffix !is String) {
            throw EvaluationException("ENDS_WITH requires string operands")
        }
        return str.endsWith(suffix)
    }

    /**
     * Evaluate MATCHES operator (regex)
     */
    private fun evaluateMatches(str: Any, pattern: Any): Boolean {
        if (str !is String || pattern !is String) {
            throw EvaluationException("MATCHES requires string operands")
        }
        return try {
            str.matches(Regex(pattern))
        } catch (e: Exception) {
            throw EvaluationException("Invalid regex pattern: $pattern", e)
        }
    }
}
