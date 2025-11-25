package com.abac.evaluator

import com.abac.dsl.*
import com.abac.model.*
import kotlin.test.*

class PolicyEvaluatorTest {

    private lateinit var evaluator: PolicyEvaluator

    @BeforeTest
    fun setup() {
        evaluator = PolicyEvaluator()
    }

    @Test
    fun `test simple equality`() {
        val expr = ExpressionBuilder().run {
            subject.role eq "Admin"
        }

        val context = evaluationContext {
            subject("role", "Admin")
        }

        assertTrue(evaluator.evaluate(expr, context))
    }

    @Test
    fun `test AND operator`() {
        val expr = ExpressionBuilder().run {
            (subject.role eq "Admin") and (resource.department eq "IT")
        }

        val context1 = evaluationContext {
            subject("role", "Admin")
            resource("department", "IT")
        }

        assertTrue(evaluator.evaluate(expr, context1))

        val context2 = evaluationContext {
            subject("role", "Admin")
            resource("department", "HR")
        }

        assertFalse(evaluator.evaluate(expr, context2))
    }

    @Test
    fun `test OR operator`() {
        val expr = ExpressionBuilder().run {
            (subject.role eq "Admin") or (subject.role eq "Manager")
        }

        val context1 = evaluationContext {
            subject("role", "Admin")
        }

        assertTrue(evaluator.evaluate(expr, context1))

        val context2 = evaluationContext {
            subject("role", "Manager")
        }

        assertTrue(evaluator.evaluate(expr, context2))

        val context3 = evaluationContext {
            subject("role", "User")
        }

        assertFalse(evaluator.evaluate(expr, context3))
    }

    @Test
    fun `test NOT operator`() {
        val expr = ExpressionBuilder().run {
            not(subject.role eq "Guest")
        }

        val context1 = evaluationContext {
            subject("role", "Admin")
        }

        assertTrue(evaluator.evaluate(expr, context1))

        val context2 = evaluationContext {
            subject("role", "Guest")
        }

        assertFalse(evaluator.evaluate(expr, context2))
    }

    @Test
    fun `test IN operator`() {
        val expr = ExpressionBuilder().run {
            subject.role isIn listOf("Admin", "Manager", "Editor")
        }

        val context1 = evaluationContext {
            subject("role", "Admin")
        }

        assertTrue(evaluator.evaluate(expr, context1))

        val context2 = evaluationContext {
            subject("role", "User")
        }

        assertFalse(evaluator.evaluate(expr, context2))
    }

    @Test
    fun `test CONTAINS operator`() {
        val expr = ExpressionBuilder().run {
            subject.assignedClients contains "Client_A"
        }

        val context1 = evaluationContext {
            subject("assignedClients", listOf("Client_A", "Client_B"))
        }

        assertTrue(evaluator.evaluate(expr, context1))

        val context2 = evaluationContext {
            subject("assignedClients", listOf("Client_B", "Client_C"))
        }

        assertFalse(evaluator.evaluate(expr, context2))
    }

    @Test
    fun `test numeric comparison`() {
        val expr = ExpressionBuilder().run {
            subject.riskScore lt 30
        }

        val context1 = evaluationContext {
            subject("riskScore", 20)
        }

        assertTrue(evaluator.evaluate(expr, context1))

        val context2 = evaluationContext {
            subject("riskScore", 40)
        }

        assertFalse(evaluator.evaluate(expr, context2))
    }

    @Test
    fun `test IMPLIES operator`() {
        val expr = ExpressionBuilder().run {
            (subject.role eq "Admin") implies (subject.riskScore lt 50)
        }

        // Admin with low risk - true
        val context1 = evaluationContext {
            subject("role", "Admin")
            subject("riskScore", 30)
        }
        assertTrue(evaluator.evaluate(expr, context1))

        // Admin with high risk - false
        val context2 = evaluationContext {
            subject("role", "Admin")
            subject("riskScore", 60)
        }
        assertFalse(evaluator.evaluate(expr, context2))

        // Non-admin - true (implication is vacuously true)
        val context3 = evaluationContext {
            subject("role", "User")
            subject("riskScore", 100)
        }
        assertTrue(evaluator.evaluate(expr, context3))
    }

    @Test
    fun `test string operations`() {
        val startsWith = ExpressionBuilder().run {
            resource["filename"] startsWith "report_"
        }

        val context1 = evaluationContext {
            resource("filename", "report_2024.pdf")
        }
        assertTrue(evaluator.evaluate(startsWith, context1))

        val endsWith = ExpressionBuilder().run {
            resource["filename"] endsWith ".pdf"
        }

        val context2 = evaluationContext {
            resource("filename", "document.pdf")
        }
        assertTrue(evaluator.evaluate(endsWith, context2))
    }

    @Test
    fun `test when expression`() {
        val expr = ExpressionBuilder().run {
            whenCase(resource.status) {
                (resource.status eq "Draft") then true
                (resource.status eq "Published") then false
                elseCase(false)
            }
        }

        val context1 = evaluationContext {
            resource("status", "Draft")
        }
        assertTrue(evaluator.evaluate(expr, context1))

        val context2 = evaluationContext {
            resource("status", "Published")
        }
        assertFalse(evaluator.evaluate(expr, context2))

        val context3 = evaluationContext {
            resource("status", "Unknown")
        }
        assertFalse(evaluator.evaluate(expr, context3))
    }

    @Test
    fun `test nested object navigation`() {
        val expr = ExpressionBuilder().run {
            subject.id eq resource.owner.managerId
        }

        val context = evaluationContext {
            subject("id", "manager123")
            resource(
                mapOf(
                    "owner" to mapOf(
                        "managerId" to "manager123"
                    )
                )
            )
        }

        assertTrue(evaluator.evaluate(expr, context))
    }

    @Test
    fun `test currentTime function`() {
        val expr = ExpressionBuilder().run {
            currentTime() gt const(0)
        }

        val context = evaluationContext {
            subject("id", "user123")
        }

        assertTrue(evaluator.evaluate(expr, context))
    }

    @Test
    fun `test between function`() {
        val expr = ExpressionBuilder().run {
            between(subject.riskScore, const(10), const(50))
        }

        val context1 = evaluationContext {
            subject("riskScore", 30)
        }
        assertTrue(evaluator.evaluate(expr, context1))

        val context2 = evaluationContext {
            subject("riskScore", 60)
        }
        assertFalse(evaluator.evaluate(expr, context2))
    }

    @Test
    fun `test hasRole function`() {
        val expr = ExpressionBuilder().run {
            hasRole("Admin")
        }

        // Single role as string
        val context1 = evaluationContext {
            subject("role", "Admin")
        }
        assertTrue(evaluator.evaluate(expr, context1))

        // Multiple roles as list
        val context2 = evaluationContext {
            subject("roles", listOf("Admin", "Manager"))
        }
        assertTrue(evaluator.evaluate(expr, context2))

        // No matching role
        val context3 = evaluationContext {
            subject("role", "User")
        }
        assertFalse(evaluator.evaluate(expr, context3))
    }

    @Test
    fun `test complex nested expression`() {
        val expr = ExpressionBuilder().run {
            ((subject.role eq "Admin") or (subject.role eq "Manager")) and
            (resource.department eq subject.department) and
            (subject.riskScore lt 50)
        }

        val context1 = evaluationContext {
            subject("role", "Admin")
            subject("department", "IT")
            subject("riskScore", 30)
            resource("department", "IT")
        }
        assertTrue(evaluator.evaluate(expr, context1))

        val context2 = evaluationContext {
            subject("role", "User")
            subject("department", "IT")
            subject("riskScore", 30)
            resource("department", "IT")
        }
        assertFalse(evaluator.evaluate(expr, context2))
    }

    @Test
    fun `test evaluation error handling`() {
        val expr = ExpressionBuilder().run {
            subject("nonexistent") eq "value"
        }

        val context = evaluationContext {
            subject("id", "user123")
        }

        assertFailsWith<EvaluationException> {
            evaluator.evaluate(expr, context)
        }
    }
}
