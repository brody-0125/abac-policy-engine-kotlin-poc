package com.abac.validator

import com.abac.model.*

/**
 * Validates policy expressions for security and complexity constraints
 */
class PolicyValidator(
    private val config: ValidationConfig = ValidationConfig()
) {
    /**
     * Configuration for validation rules
     */
    data class ValidationConfig(
        val maxDepth: Int = 10,
        val maxConditions: Int = 50,
        val maxFunctionCallDepth: Int = 5,
        val allowedOperators: Set<Operator> = Operator.values().toSet(),
        val allowedUnaryOperators: Set<UnaryOperator> = UnaryOperator.values().toSet(),
        val allowedFunctions: Set<String> = setOf(
            "currentTime", "between", "hasRole", "hasAnyRole", "hasAllRoles",
            "toDate", "dayOfWeek", "hourOfDay", "length", "toUpperCase", "toLowerCase"
        ),
        val maxListSize: Int = 100,
        val maxWhenCases: Int = 20
    )

    /**
     * Validate a policy expression
     */
    fun validate(expression: Expression): ValidationResult {
        val issues = mutableListOf<ValidationIssue>()

        val depth = calculateDepth(expression)
        if (depth > config.maxDepth) {
            issues.add(
                ValidationIssue.Complexity(
                    "Expression depth ($depth) exceeds maximum (${config.maxDepth})"
                )
            )
        }

        val conditionCount = countConditions(expression)
        if (conditionCount > config.maxConditions) {
            issues.add(
                ValidationIssue.Complexity(
                    "Too many conditions ($conditionCount), maximum is ${config.maxConditions}"
                )
            )
        }

        val unsafeOperators = findUnsafeOperators(expression)
        if (unsafeOperators.isNotEmpty()) {
            issues.add(
                ValidationIssue.Security(
                    "Unsafe operators found: ${unsafeOperators.joinToString()}"
                )
            )
        }

        val unsafeFunctions = findUnsafeFunctions(expression)
        if (unsafeFunctions.isNotEmpty()) {
            issues.add(
                ValidationIssue.Security(
                    "Unsafe functions found: ${unsafeFunctions.joinToString()}"
                )
            )
        }

        validateSecurity(expression, issues)

        return if (issues.isEmpty()) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(issues)
        }
    }

    /**
     * Calculate the maximum depth of an expression tree
     */
    private fun calculateDepth(expression: Expression, current: Int = 0): Int {
        return when (expression) {
            is Expression.Constant, is Expression.Variable -> current

            is Expression.BinaryOperation -> maxOf(
                calculateDepth(expression.left, current + 1),
                calculateDepth(expression.right, current + 1)
            )

            is Expression.UnaryOperation ->
                calculateDepth(expression.operand, current + 1)

            is Expression.FunctionCall -> {
                val argDepth = expression.arguments.maxOfOrNull {
                    calculateDepth(it, current + 1)
                } ?: current
                argDepth
            }

            is Expression.ListLiteral -> {
                expression.elements.maxOfOrNull {
                    calculateDepth(it, current + 1)
                } ?: current
            }

            is Expression.WhenExpression -> {
                val subjectDepth = calculateDepth(expression.subject, current + 1)
                val casesDepth = expression.cases.maxOfOrNull {
                    maxOf(
                        calculateDepth(it.condition, current + 1),
                        calculateDepth(it.result, current + 1)
                    )
                } ?: current
                val elseDepth = expression.elseCase?.let {
                    calculateDepth(it, current + 1)
                } ?: current
                maxOf(subjectDepth, casesDepth, elseDepth)
            }
        }
    }

    /**
     * Count the number of conditions in an expression
     */
    private fun countConditions(expression: Expression): Int {
        return when (expression) {
            is Expression.Constant, is Expression.Variable -> 0

            is Expression.BinaryOperation ->
                1 + countConditions(expression.left) + countConditions(expression.right)

            is Expression.UnaryOperation ->
                1 + countConditions(expression.operand)

            is Expression.FunctionCall ->
                1 + expression.arguments.sumOf { countConditions(it) }

            is Expression.ListLiteral ->
                expression.elements.sumOf { countConditions(it) }

            is Expression.WhenExpression -> {
                val casesCount = expression.cases.sumOf {
                    countConditions(it.condition) + countConditions(it.result)
                }
                val elseCount = expression.elseCase?.let { countConditions(it) } ?: 0
                countConditions(expression.subject) + casesCount + elseCount
            }
        }
    }

    /**
     * Find operators that are not in the allowed set
     */
    private fun findUnsafeOperators(expression: Expression): Set<Operator> {
        val found = mutableSetOf<Operator>()

        fun visit(expr: Expression) {
            when (expr) {
                is Expression.BinaryOperation -> {
                    if (expr.operator !in config.allowedOperators) {
                        found.add(expr.operator)
                    }
                    visit(expr.left)
                    visit(expr.right)
                }

                is Expression.UnaryOperation -> {
                    visit(expr.operand)
                }

                is Expression.FunctionCall -> {
                    expr.arguments.forEach { visit(it) }
                }

                is Expression.ListLiteral -> {
                    expr.elements.forEach { visit(it) }
                }

                is Expression.WhenExpression -> {
                    visit(expr.subject)
                    expr.cases.forEach {
                        visit(it.condition)
                        visit(it.result)
                    }
                    expr.elseCase?.let { visit(it) }
                }

                else -> { /* Terminal nodes */ }
            }
        }

        visit(expression)
        return found
    }

    /**
     * Find functions that are not in the allowed set
     */
    private fun findUnsafeFunctions(expression: Expression): Set<String> {
        val found = mutableSetOf<String>()

        fun visit(expr: Expression) {
            when (expr) {
                is Expression.FunctionCall -> {
                    if (expr.name !in config.allowedFunctions) {
                        found.add(expr.name)
                    }
                    expr.arguments.forEach { visit(it) }
                }

                is Expression.BinaryOperation -> {
                    visit(expr.left)
                    visit(expr.right)
                }

                is Expression.UnaryOperation -> {
                    visit(expr.operand)
                }

                is Expression.ListLiteral -> {
                    expr.elements.forEach { visit(it) }
                }

                is Expression.WhenExpression -> {
                    visit(expr.subject)
                    expr.cases.forEach {
                        visit(it.condition)
                        visit(it.result)
                    }
                    expr.elseCase?.let { visit(it) }
                }

                else -> { /* Terminal nodes */ }
            }
        }

        visit(expression)
        return found
    }

    /**
     * Validate security-specific constraints
     */
    private fun validateSecurity(expression: Expression, issues: MutableList<ValidationIssue>) {
        fun visit(expr: Expression, functionDepth: Int = 0) {
            when (expr) {
                is Expression.FunctionCall -> {
                    if (functionDepth >= config.maxFunctionCallDepth) {
                        issues.add(
                            ValidationIssue.Security(
                                "Function call depth exceeds maximum (${config.maxFunctionCallDepth})"
                            )
                        )
                    }
                    expr.arguments.forEach { visit(it, functionDepth + 1) }
                }

                is Expression.ListLiteral -> {
                    if (expr.elements.size > config.maxListSize) {
                        issues.add(
                            ValidationIssue.Security(
                                "List size (${expr.elements.size}) exceeds maximum (${config.maxListSize})"
                            )
                        )
                    }
                    expr.elements.forEach { visit(it, functionDepth) }
                }

                is Expression.WhenExpression -> {
                    if (expr.cases.size > config.maxWhenCases) {
                        issues.add(
                            ValidationIssue.Complexity(
                                "When expression has too many cases (${expr.cases.size}), maximum is ${config.maxWhenCases}"
                            )
                        )
                    }
                    visit(expr.subject, functionDepth)
                    expr.cases.forEach {
                        visit(it.condition, functionDepth)
                        visit(it.result, functionDepth)
                    }
                    expr.elseCase?.let { visit(it, functionDepth) }
                }

                is Expression.BinaryOperation -> {
                    visit(expr.left, functionDepth)
                    visit(expr.right, functionDepth)
                }

                is Expression.UnaryOperation -> {
                    visit(expr.operand, functionDepth)
                }

                else -> { /* Terminal nodes */ }
            }
        }

        visit(expression)
    }
}

/**
 * Result of policy validation
 */
sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Invalid(val issues: List<ValidationIssue>) : ValidationResult()

    fun isValid(): Boolean = this is Valid
    fun isInvalid(): Boolean = this is Invalid

    fun throwIfInvalid() {
        if (this is Invalid) {
            throw ValidationException(
                "Policy validation failed:\n" + issues.joinToString("\n") { "  - $it" }
            )
        }
    }
}

/**
 * Types of validation issues
 */
sealed class ValidationIssue {
    abstract val message: String

    data class Security(override val message: String) : ValidationIssue()
    data class Complexity(override val message: String) : ValidationIssue()
    data class Syntax(override val message: String) : ValidationIssue()

    override fun toString(): String {
        return "[${this::class.simpleName}] $message"
    }
}
