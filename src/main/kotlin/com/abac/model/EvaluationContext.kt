package com.abac.model

data class EvaluationContext(
    val subject: Map<String, Any>,
    val action: Any,
    val resource: Map<String, Any>,
    val environment: Map<String, Any> = emptyMap()
) {
    fun resolve(path: String): Any? {
        val parts = path.split(".")

        return when (parts[0]) {
            "subject" -> subject.navigatePath(parts.drop(1))
            "resource" -> resource.navigatePath(parts.drop(1))
            "action" -> {
                if (parts.size == 1) {
                    action
                } else {
                    when (action) {
                        is Map<*, *> -> (action as Map<String, Any>).navigatePath(parts.drop(1))
                        else -> null
                    }
                }
            }
            "environment" -> environment.navigatePath(parts.drop(1))
            else -> null
        }
    }

    private fun Map<String, Any>.navigatePath(path: List<String>): Any? {
        if (path.isEmpty()) return this

        val current = this[path[0]] ?: return null
        if (path.size == 1) return current

        return when (current) {
            is Map<*, *> -> (current as Map<String, Any>).navigatePath(path.drop(1))
            else -> null
        }
    }
}

class EvaluationContextBuilder {
    private val subject = mutableMapOf<String, Any>()
    private var action: Any = ""
    private val resource = mutableMapOf<String, Any>()
    private val environment = mutableMapOf<String, Any>()

    fun subject(key: String, value: Any) = apply { subject[key] = value }
    fun subject(values: Map<String, Any>) = apply { subject.putAll(values) }

    fun action(value: String) = apply { action = value }
    fun action(value: Map<String, Any>) = apply { action = value }

    fun resource(key: String, value: Any) = apply { resource[key] = value }
    fun resource(values: Map<String, Any>) = apply { resource.putAll(values) }

    fun environment(key: String, value: Any) = apply { environment[key] = value }
    fun environment(values: Map<String, Any>) = apply { environment.putAll(values) }

    fun build() = EvaluationContext(
        subject = subject.toMap(),
        action = action,
        resource = resource.toMap(),
        environment = environment.toMap()
    )
}

fun evaluationContext(block: EvaluationContextBuilder.() -> Unit): EvaluationContext {
    return EvaluationContextBuilder().apply(block).build()
}
