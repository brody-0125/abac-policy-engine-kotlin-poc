package com.abac

import com.abac.model.*
import com.abac.examples.AdvancedScenarios
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class AbacEngineTest {

    private lateinit var engine: AbacEngine
    private lateinit var repository: InMemoryPolicyRepository

    @BeforeTest
    fun setup() {
        repository = InMemoryPolicyRepository()
        engine = AbacEngine(policyRepository = repository)
    }

    @Test
    fun `test separation of duties - deny self-approval`() = runTest {
        val policy = AdvancedScenarios.separationOfDuties()
        engine.storePolicy(policy)

        // Creator tries to approve their own expense report - should be denied
        val context = evaluationContext {
            subject("id", "user123")
            subject("role", "Manager")
            action("Approve")
            resource("ownerId", "user123") // Same as subject.id
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertFalse((decision as AccessDecision.Decision).allowed)
    }

    @Test
    fun `test separation of duties - allow different approver`() = runTest {
        val policy = AdvancedScenarios.separationOfDuties()
        engine.storePolicy(policy)

        // Different manager approves - should be allowed
        val context = evaluationContext {
            subject("id", "manager456")
            subject("role", "Manager")
            action("Approve")
            resource("ownerId", "user123") // Different from subject.id
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertTrue((decision as AccessDecision.Decision).allowed)
    }

    @Test
    fun `test role-based access - admin can access any department`() = runTest {
        val policy = AdvancedScenarios.roleBasedAccess()
        engine.storePolicy(policy)

        val context = evaluationContext {
            subject("role", "ADMIN")
            subject("department", "Engineering")
            resource("department", "Finance") // Different department
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertTrue((decision as AccessDecision.Decision).allowed)
    }

    @Test
    fun `test role-based access - manager can only access own department`() = runTest {
        val policy = AdvancedScenarios.roleBasedAccess()
        engine.storePolicy(policy)

        // Manager accessing own department - allowed
        val context1 = evaluationContext {
            subject("role", "MANAGER")
            subject("department", "Engineering")
            resource("department", "Engineering")
        }

        val decision1 = engine.checkAccess(policy.id, context1)
        assertTrue(decision1 is AccessDecision.Decision)
        assertTrue((decision1 as AccessDecision.Decision).allowed)

        // Manager accessing different department - denied
        val context2 = evaluationContext {
            subject("role", "MANAGER")
            subject("department", "Engineering")
            resource("department", "Finance")
        }

        val decision2 = engine.checkAccess(policy.id, context2)
        assertTrue(decision2 is AccessDecision.Decision)
        assertFalse((decision2 as AccessDecision.Decision).allowed)
    }

    @Test
    fun `test resource lifecycle - draft accessible by creator only`() = runTest {
        val policy = AdvancedScenarios.resourceLifecycle()
        engine.storePolicy(policy)

        // Creator accessing draft - allowed
        val context1 = evaluationContext {
            subject("id", "user123")
            resource("status", "Draft")
            resource("creatorId", "user123")
        }

        val decision1 = engine.checkAccess(policy.id, context1)
        assertTrue(decision1 is AccessDecision.Decision)
        assertTrue((decision1 as AccessDecision.Decision).allowed)

        // Non-creator accessing draft - denied
        val context2 = evaluationContext {
            subject("id", "user456")
            resource("status", "Draft")
            resource("creatorId", "user123")
        }

        val decision2 = engine.checkAccess(policy.id, context2)
        assertTrue(decision2 is AccessDecision.Decision)
        assertFalse((decision2 as AccessDecision.Decision).allowed)
    }

    @Test
    fun `test hierarchical access - manager can view subordinate reviews`() = runTest {
        val policy = AdvancedScenarios.hierarchicalAccess()
        engine.storePolicy(policy)

        // Manager viewing their subordinate's review - allowed
        val context1 = evaluationContext {
            subject("id", "manager123")
            action("View_Performance_Review")
            resource(
                mapOf(
                    "owner" to mapOf(
                        "managerId" to "manager123"
                    )
                )
            )
        }

        val decision1 = engine.checkAccess(policy.id, context1)
        assertTrue(decision1 is AccessDecision.Decision)
        assertTrue((decision1 as AccessDecision.Decision).allowed)

        // Non-manager viewing review - denied
        val context2 = evaluationContext {
            subject("id", "other456")
            action("View_Performance_Review")
            resource(
                mapOf(
                    "owner" to mapOf(
                        "managerId" to "manager123"
                    )
                )
            )
        }

        val decision2 = engine.checkAccess(policy.id, context2)
        assertTrue(decision2 is AccessDecision.Decision)
        assertFalse((decision2 as AccessDecision.Decision).allowed)
    }

    @Test
    fun `test purpose-based access - HR can access PII for salary processing`() = runTest {
        val policy = AdvancedScenarios.purposeBasedAccess()
        engine.storePolicy(policy)

        // HR accessing PII for salary processing - allowed
        val context1 = evaluationContext {
            subject("role", "HR")
            action(mapOf("purpose" to "Salary_Processing"))
            resource("type", "PII_Data")
        }

        val decision1 = engine.checkAccess(policy.id, context1)
        assertTrue(decision1 is AccessDecision.Decision)
        assertTrue((decision1 as AccessDecision.Decision).allowed)

        // HR accessing PII for different purpose - denied
        val context2 = evaluationContext {
            subject("role", "HR")
            action(mapOf("purpose" to "Marketing"))
            resource("type", "PII_Data")
        }

        val decision2 = engine.checkAccess(policy.id, context2)
        assertTrue(decision2 is AccessDecision.Decision)
        assertFalse((decision2 as AccessDecision.Decision).allowed)
    }

    @Test
    fun `test risk-based control - high risk user cannot delete`() = runTest {
        val policy = AdvancedScenarios.riskBasedControl()
        engine.storePolicy(policy)

        // Low risk admin - allowed
        val context1 = evaluationContext {
            subject("role", "Admin")
            subject("riskScore", 20)
            action("Delete_Table")
        }

        val decision1 = engine.checkAccess(policy.id, context1)
        assertTrue(decision1 is AccessDecision.Decision)
        assertTrue((decision1 as AccessDecision.Decision).allowed)

        // High risk admin - denied
        val context2 = evaluationContext {
            subject("role", "Admin")
            subject("riskScore", 50)
            action("Delete_Table")
        }

        val decision2 = engine.checkAccess(policy.id, context2)
        assertTrue(decision2 is AccessDecision.Decision)
        assertFalse((decision2 as AccessDecision.Decision).allowed)
    }

    @Test
    fun `test usage quota - basic user within limit`() = runTest {
        val policy = AdvancedScenarios.usageQuota()
        engine.storePolicy(policy)

        // Within quota - allowed
        val context1 = evaluationContext {
            subject("subLevel", "Basic")
            subject("dailyUsage", 50)
            action("API_Call")
        }

        val decision1 = engine.checkAccess(policy.id, context1)
        assertTrue(decision1 is AccessDecision.Decision)
        assertTrue((decision1 as AccessDecision.Decision).allowed)

        // Exceeded quota - denied
        val context2 = evaluationContext {
            subject("subLevel", "Basic")
            subject("dailyUsage", 150)
            action("API_Call")
        }

        val decision2 = engine.checkAccess(policy.id, context2)
        assertTrue(decision2 is AccessDecision.Decision)
        assertFalse((decision2 as AccessDecision.Decision).allowed)
    }

    @Test
    fun `test ethical wall - conflict of interest prevention`() = runTest {
        val policy = AdvancedScenarios.ethicalWall()
        engine.storePolicy(policy)

        // User not assigned to Client B - allowed
        val context1 = evaluationContext {
            subject("assignedClients", listOf("Client_C", "Client_D"))
            resource("client", "Client_A")
        }

        val decision1 = engine.checkAccess(policy.id, context1)
        assertTrue(decision1 is AccessDecision.Decision)
        assertTrue((decision1 as AccessDecision.Decision).allowed)

        // User assigned to Client B - denied (conflict of interest)
        val context2 = evaluationContext {
            subject("assignedClients", listOf("Client_B", "Client_C"))
            resource("client", "Client_A")
        }

        val decision2 = engine.checkAccess(policy.id, context2)
        assertTrue(decision2 is AccessDecision.Decision)
        assertFalse((decision2 as AccessDecision.Decision).allowed)
    }

    @Test
    fun `test cache functionality`() = runTest {
        val policy = AdvancedScenarios.roleBasedAccess()
        engine.storePolicy(policy)

        val context = evaluationContext {
            subject("role", "ADMIN")
            subject("department", "Engineering")
            resource("department", "Finance")
        }

        // First access - cache miss
        val decision1 = engine.checkAccess(policy.id, context)
        assertTrue(decision1 is AccessDecision.Decision)

        // Second access - cache hit (should be faster)
        val decision2 = engine.checkAccess(policy.id, context)
        assertTrue(decision2 is AccessDecision.Decision)

        // Verify cache statistics
        val stats = engine.getCacheStatistics()
        assertEquals(1, stats.size)
        assertTrue(stats.totalHits >= 0)

        // Clear cache
        engine.clearCache()
        val statsAfterClear = engine.getCacheStatistics()
        assertEquals(0, statsAfterClear.size)
    }

    @Test
    fun `test JSON serialization round-trip`() {
        val policy = AdvancedScenarios.separationOfDuties()

        // Serialize to JSON
        val json = engine.serializePolicy(policy)
        assertNotNull(json)
        assertTrue(json.contains("BinaryOp"))

        // Deserialize back
        val deserialized = engine.deserializePolicy(json)
        assertEquals(policy.id, deserialized.id)
        assertEquals(policy.name, deserialized.name)
    }

    @Test
    fun `test policy validation - valid policy`() {
        val policy = AdvancedScenarios.roleBasedAccess()
        val result = engine.validatePolicy(policy)

        assertTrue(result.isValid())
    }

    @Test
    fun `test multi-policy evaluation - all must pass`() = runTest {
        val policy1 = AdvancedScenarios.roleBasedAccess()
        val policy2 = AdvancedScenarios.riskBasedControl()

        engine.storePolicy(policy1)
        engine.storePolicy(policy2)

        val context = evaluationContext {
            subject("role", "Admin")
            subject("department", "Engineering")
            subject("riskScore", 20)
            action("Delete_Table")
            resource("department", "Engineering")
        }

        val results = engine.checkAccessAll(
            listOf(policy1.id, policy2.id),
            context
        )

        assertEquals(2, results.size)
        results.values.forEach { decision ->
            assertTrue(decision is AccessDecision.Decision)
            assertTrue((decision as AccessDecision.Decision).allowed)
        }
    }

    @Test
    fun `test break-glass emergency access`() = runTest {
        val policy = AdvancedScenarios.breakGlassAccess()
        engine.storePolicy(policy)

        // Emergency with justification - allowed
        val context1 = evaluationContext {
            environment("emergency", true)
            environment("justification", "Patient life-threatening situation")
        }

        val decision1 = engine.checkAccess(policy.id, context1)
        assertTrue(decision1 is AccessDecision.Decision)
        assertTrue((decision1 as AccessDecision.Decision).allowed)

        // Non-emergency - denied
        val context2 = evaluationContext {
            environment("emergency", false)
        }

        val decision2 = engine.checkAccess(policy.id, context2)
        assertTrue(decision2 is AccessDecision.Decision)
        assertFalse((decision2 as AccessDecision.Decision).allowed)
    }
}
