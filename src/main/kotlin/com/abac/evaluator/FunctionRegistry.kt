package com.abac.evaluator

import com.abac.model.EvaluationContext
import com.abac.model.EvaluationException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class FunctionRegistry {
    private val functions = mutableMapOf<String, PolicyFunction>()

    init {
        registerBuiltInFunctions()
    }

    fun register(name: String, function: PolicyFunction) {
        functions[name] = function
    }

    fun get(name: String): PolicyFunction? = functions[name]

    fun has(name: String): Boolean = functions.containsKey(name)

    private fun registerBuiltInFunctions() {
        register("currentTime") { args, _ ->
            if (args.isNotEmpty()) {
                throw EvaluationException("currentTime() takes no arguments")
            }
            System.currentTimeMillis()
        }

        register("between") { args, _ ->
            if (args.size != 3) {
                throw EvaluationException("between() requires exactly 3 arguments: value, start, end")
            }
            val value = args[0]
            val start = args[1]
            val end = args[2]

            when {
                value is Number && start is Number && end is Number -> {
                    val v = value.toDouble()
                    val s = start.toDouble()
                    val e = end.toDouble()
                    v >= s && v <= e
                }
                value is Comparable<*> && start is Comparable<*> && end is Comparable<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    val v = value as Comparable<Any>
                    @Suppress("UNCHECKED_CAST")
                    val s = start as Comparable<Any>
                    @Suppress("UNCHECKED_CAST")
                    val e = end as Comparable<Any>
                    v >= s && v <= e
                }
                else -> throw EvaluationException(
                    "between() requires comparable arguments, got: ${value::class}, ${start::class}, ${end::class}"
                )
            }
        }

        register("hasRole") { args, context ->
            if (args.size != 1) {
                throw EvaluationException("hasRole() requires exactly 1 argument: role")
            }
            val role = args[0] as? String
                ?: throw EvaluationException("hasRole() argument must be a string")

            val subjectRole = context.resolve("subject.role")
            val subjectRoles = context.resolve("subject.roles")

            when {
                subjectRole is String -> subjectRole == role
                subjectRoles is Collection<*> -> role in subjectRoles
                else -> false
            }
        }

        register("hasAnyRole") { args, context ->
            if (args.isEmpty()) {
                throw EvaluationException("hasAnyRole() requires at least 1 argument")
            }
            val roles = args.map { it as? String ?: throw EvaluationException("All arguments must be strings") }

            val subjectRole = context.resolve("subject.role")
            val subjectRoles = context.resolve("subject.roles")

            when {
                subjectRole is String -> subjectRole in roles
                subjectRoles is Collection<*> -> roles.any { it in subjectRoles }
                else -> false
            }
        }

        register("hasAllRoles") { args, context ->
            if (args.isEmpty()) {
                throw EvaluationException("hasAllRoles() requires at least 1 argument")
            }
            val roles = args.map { it as? String ?: throw EvaluationException("All arguments must be strings") }

            val subjectRoles = context.resolve("subject.roles") as? Collection<*>
                ?: return@register false

            roles.all { it in subjectRoles }
        }

        register("toDate") { args, _ ->
            if (args.size != 1) {
                throw EvaluationException("toDate() requires exactly 1 argument: timestamp")
            }
            val timestamp = args[0] as? Number
                ?: throw EvaluationException("toDate() argument must be a number")

            LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp.toLong()), ZoneOffset.UTC)
        }

        register("dayOfWeek") { args, _ ->
            if (args.size != 1) {
                throw EvaluationException("dayOfWeek() requires exactly 1 argument: timestamp")
            }
            val timestamp = args[0] as? Number
                ?: throw EvaluationException("dayOfWeek() argument must be a number")

            LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp.toLong()), ZoneOffset.UTC)
                .dayOfWeek.value
        }

        register("hourOfDay") { args, _ ->
            if (args.size != 1) {
                throw EvaluationException("hourOfDay() requires exactly 1 argument: timestamp")
            }
            val timestamp = args[0] as? Number
                ?: throw EvaluationException("hourOfDay() argument must be a number")

            LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp.toLong()), ZoneOffset.UTC)
                .hour
        }

        register("length") { args, _ ->
            if (args.size != 1) {
                throw EvaluationException("length() requires exactly 1 argument: string")
            }
            val str = args[0] as? String
                ?: throw EvaluationException("length() argument must be a string")
            str.length
        }

        register("toUpperCase") { args, _ ->
            if (args.size != 1) {
                throw EvaluationException("toUpperCase() requires exactly 1 argument: string")
            }
            val str = args[0] as? String
                ?: throw EvaluationException("toUpperCase() argument must be a string")
            str.uppercase()
        }

        register("toLowerCase") { args, _ ->
            if (args.size != 1) {
                throw EvaluationException("toLowerCase() requires exactly 1 argument: string")
            }
            val str = args[0] as? String
                ?: throw EvaluationException("toLowerCase() argument must be a string")
            str.lowercase()
        }
    }
}

typealias PolicyFunction = (args: List<Any>, context: EvaluationContext) -> Any
