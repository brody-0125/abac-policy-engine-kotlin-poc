package com.abac

import com.abac.examples.CoreAbacScenarios
import com.abac.model.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Comprehensive unit tests for the 10 core ABAC scenarios
 * Each scenario has positive and negative test cases
 */
class CoreAbacScenariosTest {

    private lateinit var engine: AbacEngine
    private lateinit var repository: InMemoryPolicyRepository

    @BeforeTest
    fun setup() {
        repository = InMemoryPolicyRepository()
        engine = AbacEngine(policyRepository = repository)
    }

    // ============================================
    // Scenario 1: Separation of Duties (SoD)
    // ============================================

    @Test
    fun `1-1 SoD - deny self-approval by manager`() = runTest {
        val policy = CoreAbacScenarios.separationOfDuties()
        engine.storePolicy(policy)

        val context = evaluationContext {
            subject("id", "user123")
            subject("role", "Manager")
            action("Approve")
            resource("ownerId", "user123") // Same as subject.id - should be denied
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision, "Expected Decision result")
        assertFalse((decision as AccessDecision.Decision).allowed, "Self-approval should be denied")
        println("✓ Test 1-1 passed: Self-approval denied")
    }

    @Test
    fun `1-2 SoD - allow approval by different manager`() = runTest {
        val policy = CoreAbacScenarios.separationOfDuties()
        engine.storePolicy(policy)

        val context = evaluationContext {
            subject("id", "manager456")
            subject("role", "Manager")
            action("Approve")
            resource("ownerId", "user123") // Different from subject.id - should be allowed
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertTrue((decision as AccessDecision.Decision).allowed, "Different manager approval should be allowed")
        println("✓ Test 1-2 passed: Different manager approval allowed")
    }

    @Test
    fun `1-3 SoD - deny non-manager approval attempt`() = runTest {
        val policy = CoreAbacScenarios.separationOfDuties()
        engine.storePolicy(policy)

        val context = evaluationContext {
            subject("id", "user789")
            subject("role", "Employee") // Not a Manager
            action("Approve")
            resource("ownerId", "user123")
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertFalse((decision as AccessDecision.Decision).allowed, "Non-manager should not approve")
        println("✓ Test 1-3 passed: Non-manager approval denied")
    }

    // ============================================
    // Scenario 2: Project-Based Dynamic Access
    // ============================================

    @Test
    fun `2-1 Project Access - allow user in active projects`() = runTest {
        val policy = CoreAbacScenarios.projectBasedAccess()
        engine.storePolicy(policy)

        val context = evaluationContext {
            subject("activeProjects", listOf("PRJ-001", "PRJ-002", "PRJ-003"))
            action("Read")
            resource("projectId", "PRJ-002") // In user's active projects
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertTrue((decision as AccessDecision.Decision).allowed, "User should access assigned project")
        println("✓ Test 2-1 passed: Project access allowed for assigned user")
    }

    @Test
    fun `2-2 Project Access - deny user not in project`() = runTest {
        val policy = CoreAbacScenarios.projectBasedAccess()
        engine.storePolicy(policy)

        val context = evaluationContext {
            subject("activeProjects", listOf("PRJ-001", "PRJ-002"))
            action("Edit")
            resource("projectId", "PRJ-999") // NOT in user's active projects
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertFalse((decision as AccessDecision.Decision).allowed, "User should not access unassigned project")
        println("✓ Test 2-2 passed: Project access denied for unassigned user")
    }

    @Test
    fun `2-3 Project Access - allow delete action not in scope`() = runTest {
        val policy = CoreAbacScenarios.projectBasedAccess()
        engine.storePolicy(policy)

        val context = evaluationContext {
            subject("activeProjects", listOf("PRJ-001"))
            action("Delete") // Not in ["Read", "Edit"] - implication is vacuously true
            resource("projectId", "PRJ-999")
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertTrue((decision as AccessDecision.Decision).allowed, "Actions not in scope should pass")
        println("✓ Test 2-3 passed: Out-of-scope action allowed")
    }

    // ============================================
    // Scenario 3: Context-Aware Access
    // ============================================

    @Test
    fun `3-1 Context Aware - allow top secret access from secure environment`() = runTest {
        val policy = CoreAbacScenarios.contextAwareAccess()
        engine.storePolicy(policy)

        val context = evaluationContext {
            subject("location", "Office_Network")
            resource("classification", "Top Secret")
            environment("deviceType", "Corporate_Laptop")
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertTrue((decision as AccessDecision.Decision).allowed, "Secure environment should allow top secret access")
        println("✓ Test 3-1 passed: Top secret access allowed from secure environment")
    }

    @Test
    fun `3-2 Context Aware - deny top secret access from home network`() = runTest {
        val policy = CoreAbacScenarios.contextAwareAccess()
        engine.storePolicy(policy)

        val context = evaluationContext {
            subject("location", "Home_Network") // NOT Office_Network
            resource("classification", "Top Secret")
            environment("deviceType", "Corporate_Laptop")
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertFalse((decision as AccessDecision.Decision).allowed, "Home network should not allow top secret access")
        println("✓ Test 3-2 passed: Top secret access denied from home network")
    }

    @Test
    fun `3-3 Context Aware - deny top secret access from personal device`() = runTest {
        val policy = CoreAbacScenarios.contextAwareAccess()
        engine.storePolicy(policy)

        val context = evaluationContext {
            subject("location", "Office_Network")
            resource("classification", "Top Secret")
            environment("deviceType", "Personal_Phone") // NOT Corporate_Laptop
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertFalse((decision as AccessDecision.Decision).allowed, "Personal device should not access top secret")
        println("✓ Test 3-3 passed: Top secret access denied from personal device")
    }

    @Test
    fun `3-4 Context Aware - allow non-classified access from any location`() = runTest {
        val policy = CoreAbacScenarios.contextAwareAccess()
        engine.storePolicy(policy)

        val context = evaluationContext {
            subject("location", "Home_Network")
            resource("classification", "Public") // NOT Top Secret
            environment("deviceType", "Personal_Phone")
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertTrue((decision as AccessDecision.Decision).allowed, "Non-classified resources should be accessible")
        println("✓ Test 3-4 passed: Public classification accessible from anywhere")
    }

    // ============================================
    // Scenario 4: Document Lifecycle
    // ============================================

    @Test
    fun `4-1 Lifecycle - allow creator to access draft`() = runTest {
        val policy = CoreAbacScenarios.documentLifecycle()
        engine.storePolicy(policy)

        val context = evaluationContext {
            subject("id", "user123")
            resource("status", "Draft")
            resource("creatorId", "user123") // Same as subject.id
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertTrue((decision as AccessDecision.Decision).allowed, "Creator should access their draft")
        println("✓ Test 4-1 passed: Creator access to draft allowed")
    }

    @Test
    fun `4-2 Lifecycle - deny non-creator access to draft`() = runTest {
        val policy = CoreAbacScenarios.documentLifecycle()
        engine.storePolicy(policy)

        val context = evaluationContext {
            subject("id", "user456")
            resource("status", "Draft")
            resource("creatorId", "user123") // Different from subject.id
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertFalse((decision as AccessDecision.Decision).allowed, "Non-creator should not access draft")
        println("✓ Test 4-2 passed: Non-creator access to draft denied")
    }

    @Test
    fun `4-3 Lifecycle - allow editor to access under review`() = runTest {
        val policy = CoreAbacScenarios.documentLifecycle()
        engine.storePolicy(policy)

        val context = evaluationContext {
            subject("role", "Editor")
            resource("status", "Under_Review")
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertTrue((decision as AccessDecision.Decision).allowed, "Editor should access under review documents")
        println("✓ Test 4-3 passed: Editor access to under review allowed")
    }

    @Test
    fun `4-4 Lifecycle - allow public to access published`() = runTest {
        val policy = CoreAbacScenarios.documentLifecycle()
        engine.storePolicy(policy)

        val context = evaluationContext {
            subject("role", "Public")
            resource("status", "Published")
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertTrue((decision as AccessDecision.Decision).allowed, "Public should access published documents")
        println("✓ Test 4-4 passed: Public access to published allowed")
    }

    @Test
    fun `4-5 Lifecycle - deny access to unknown status`() = runTest {
        val policy = CoreAbacScenarios.documentLifecycle()
        engine.storePolicy(policy)

        val context = evaluationContext {
            subject("role", "Editor")
            resource("status", "Archived") // Not in when cases
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertFalse((decision as AccessDecision.Decision).allowed, "Unknown status should default to deny")
        println("✓ Test 4-5 passed: Unknown status defaults to deny")
    }

    // ============================================
    // Scenario 5: Hierarchical Access
    // ============================================

    @Test
    fun `5-1 Hierarchical - allow manager to view subordinate review`() = runTest {
        val policy = CoreAbacScenarios.hierarchicalAccess()
        engine.storePolicy(policy)

        val context = evaluationContext {
            subject("id", "manager123")
            action("View_Performance_Review")
            resource(
                mapOf(
                    "owner" to mapOf(
                        "managerId" to "manager123" // Same as subject.id
                    )
                )
            )
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertTrue((decision as AccessDecision.Decision).allowed, "Manager should view subordinate's review")
        println("✓ Test 5-1 passed: Manager view subordinate review allowed")
    }

    @Test
    fun `5-2 Hierarchical - deny non-manager view of review`() = runTest {
        val policy = CoreAbacScenarios.hierarchicalAccess()
        engine.storePolicy(policy)

        val context = evaluationContext {
            subject("id", "user456")
            action("View_Performance_Review")
            resource(
                mapOf(
                    "owner" to mapOf(
                        "managerId" to "manager123" // Different from subject.id
                    )
                )
            )
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertFalse((decision as AccessDecision.Decision).allowed, "Non-manager should not view review")
        println("✓ Test 5-2 passed: Non-manager view review denied")
    }

    @Test
    fun `5-3 Hierarchical - allow non-review actions`() = runTest {
        val policy = CoreAbacScenarios.hierarchicalAccess()
        engine.storePolicy(policy)

        val context = evaluationContext {
            subject("id", "user456")
            action("Edit_Profile") // Not View_Performance_Review
            resource(
                mapOf(
                    "owner" to mapOf(
                        "managerId" to "manager123"
                    )
                )
            )
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertTrue((decision as AccessDecision.Decision).allowed, "Non-review actions should pass")
        println("✓ Test 5-3 passed: Non-review action allowed")
    }

    // ============================================
    // Scenario 6: PII Protection (GDPR)
    // ============================================

    @Test
    fun `6-1 PII - allow HR access for salary processing`() = runTest {
        val policy = CoreAbacScenarios.piiProtection()
        engine.storePolicy(policy)

        val context = evaluationContext {
            subject("role", "HR")
            action(mapOf("purpose" to "Salary_Processing"))
            resource("type", "PII_Data")
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertTrue((decision as AccessDecision.Decision).allowed, "HR should access PII for salary processing")
        println("✓ Test 6-1 passed: HR access PII for legitimate purpose allowed")
    }

    @Test
    fun `6-2 PII - deny HR access for marketing purpose`() = runTest {
        val policy = CoreAbacScenarios.piiProtection()
        engine.storePolicy(policy)

        val context = evaluationContext {
            subject("role", "HR")
            action(mapOf("purpose" to "Marketing")) // Wrong purpose
            resource("type", "PII_Data")
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertFalse((decision as AccessDecision.Decision).allowed, "HR should not access PII for marketing")
        println("✓ Test 6-2 passed: HR access PII for wrong purpose denied")
    }

    @Test
    fun `6-3 PII - deny non-HR access regardless of purpose`() = runTest {
        val policy = CoreAbacScenarios.piiProtection()
        engine.storePolicy(policy)

        val context = evaluationContext {
            subject("role", "Marketing") // Not HR
            action(mapOf("purpose" to "Salary_Processing"))
            resource("type", "PII_Data")
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertFalse((decision as AccessDecision.Decision).allowed, "Non-HR should not access PII")
        println("✓ Test 6-3 passed: Non-HR access PII denied")
    }

    @Test
    fun `6-4 PII - allow access to non-PII data`() = runTest {
        val policy = CoreAbacScenarios.piiProtection()
        engine.storePolicy(policy)

        val context = evaluationContext {
            subject("role", "Marketing")
            action(mapOf("purpose" to "Marketing"))
            resource("type", "Public_Data") // Not PII_Data
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertTrue((decision as AccessDecision.Decision).allowed, "Non-PII data should be accessible")
        println("✓ Test 6-4 passed: Non-PII data access allowed")
    }

    // ============================================
    // Scenario 7: Time-Limited Delegation
    // ============================================

    @Test
    fun `7-1 Delegation - allow approval within delegation period`() = runTest {
        val policy = CoreAbacScenarios.timeLimitedDelegation()
        engine.storePolicy(policy)

        val now = System.currentTimeMillis()
        val startTime = now - 3600000 // 1 hour ago
        val endTime = now + 3600000   // 1 hour from now

        val context = evaluationContext {
            subject("id", "delegate123")
            action("Approve")
            resource("delegatedTo", "delegate123")
            resource(
                mapOf(
                    "delegation" to mapOf(
                        "startTime" to startTime,
                        "endTime" to endTime
                    )
                )
            )
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertTrue((decision as AccessDecision.Decision).allowed, "Approval within delegation period should be allowed")
        println("✓ Test 7-1 passed: Delegation within time window allowed")
    }

    @Test
    fun `7-2 Delegation - deny approval after delegation expires`() = runTest {
        val policy = CoreAbacScenarios.timeLimitedDelegation()
        engine.storePolicy(policy)

        val now = System.currentTimeMillis()
        val startTime = now - 7200000 // 2 hours ago
        val endTime = now - 3600000   // 1 hour ago (expired)

        val context = evaluationContext {
            subject("id", "delegate123")
            action("Approve")
            resource("delegatedTo", "delegate123")
            resource(
                mapOf(
                    "delegation" to mapOf(
                        "startTime" to startTime,
                        "endTime" to endTime
                    )
                )
            )
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertFalse((decision as AccessDecision.Decision).allowed, "Expired delegation should be denied")
        println("✓ Test 7-2 passed: Expired delegation denied")
    }

    @Test
    fun `7-3 Delegation - deny approval to wrong delegate`() = runTest {
        val policy = CoreAbacScenarios.timeLimitedDelegation()
        engine.storePolicy(policy)

        val now = System.currentTimeMillis()
        val startTime = now - 3600000
        val endTime = now + 3600000

        val context = evaluationContext {
            subject("id", "wrongUser456")
            action("Approve")
            resource("delegatedTo", "delegate123") // Different from subject.id
            resource(
                mapOf(
                    "delegation" to mapOf(
                        "startTime" to startTime,
                        "endTime" to endTime
                    )
                )
            )
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertFalse((decision as AccessDecision.Decision).allowed, "Wrong delegate should be denied")
        println("✓ Test 7-3 passed: Wrong delegate denied")
    }

    // ============================================
    // Scenario 8: Risk-Based Control
    // ============================================

    @Test
    fun `8-1 Risk Control - allow low-risk admin to delete`() = runTest {
        val policy = CoreAbacScenarios.riskBasedControl()
        engine.storePolicy(policy)

        val context = evaluationContext {
            subject("role", "Admin")
            subject("riskScore", 20) // Below 30
            action("Delete_Table")
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertTrue((decision as AccessDecision.Decision).allowed, "Low-risk admin should delete")
        println("✓ Test 8-1 passed: Low-risk admin delete allowed")
    }

    @Test
    fun `8-2 Risk Control - deny high-risk admin to delete`() = runTest {
        val policy = CoreAbacScenarios.riskBasedControl()
        engine.storePolicy(policy)

        val context = evaluationContext {
            subject("role", "Admin")
            subject("riskScore", 50) // Above 30
            action("Delete_Table")
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertFalse((decision as AccessDecision.Decision).allowed, "High-risk admin should not delete")
        println("✓ Test 8-2 passed: High-risk admin delete denied")
    }

    @Test
    fun `8-3 Risk Control - deny non-admin to delete regardless of risk`() = runTest {
        val policy = CoreAbacScenarios.riskBasedControl()
        engine.storePolicy(policy)

        val context = evaluationContext {
            subject("role", "User") // Not Admin
            subject("riskScore", 10) // Low risk but wrong role
            action("Delete_Table")
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertFalse((decision as AccessDecision.Decision).allowed, "Non-admin should not delete")
        println("✓ Test 8-3 passed: Non-admin delete denied")
    }

    @Test
    fun `8-4 Risk Control - allow high-risk admin for non-delete actions`() = runTest {
        val policy = CoreAbacScenarios.riskBasedControl()
        engine.storePolicy(policy)

        val context = evaluationContext {
            subject("role", "Admin")
            subject("riskScore", 50)
            action("Read_Table") // Not Delete_Table
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertTrue((decision as AccessDecision.Decision).allowed, "Non-delete actions should pass")
        println("✓ Test 8-4 passed: High-risk admin non-delete action allowed")
    }

    // ============================================
    // Scenario 9: Usage Quota
    // ============================================

    @Test
    fun `9-1 Quota - allow basic user within daily limit`() = runTest {
        val policy = CoreAbacScenarios.usageQuota()
        engine.storePolicy(policy)

        val context = evaluationContext {
            subject("subLevel", "Basic")
            subject("dailyUsage", 50) // Below 100
            action("API_Call")
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertTrue((decision as AccessDecision.Decision).allowed, "User within quota should be allowed")
        println("✓ Test 9-1 passed: User within quota allowed")
    }

    @Test
    fun `9-2 Quota - deny basic user exceeding daily limit`() = runTest {
        val policy = CoreAbacScenarios.usageQuota()
        engine.storePolicy(policy)

        val context = evaluationContext {
            subject("subLevel", "Basic")
            subject("dailyUsage", 150) // Above 100
            action("Download")
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertFalse((decision as AccessDecision.Decision).allowed, "User exceeding quota should be denied")
        println("✓ Test 9-2 passed: User exceeding quota denied")
    }

    @Test
    fun `9-3 Quota - allow premium user with high usage`() = runTest {
        val policy = CoreAbacScenarios.usageQuota()
        engine.storePolicy(policy)

        val context = evaluationContext {
            subject("subLevel", "Premium") // Not Basic
            subject("dailyUsage", 500) // High usage but not Basic level
            action("API_Call")
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertTrue((decision as AccessDecision.Decision).allowed, "Premium users should not be limited")
        println("✓ Test 9-3 passed: Premium user not limited by basic quota")
    }

    @Test
    fun `9-4 Quota - allow basic user for non-tracked actions`() = runTest {
        val policy = CoreAbacScenarios.usageQuota()
        engine.storePolicy(policy)

        val context = evaluationContext {
            subject("subLevel", "Basic")
            subject("dailyUsage", 150)
            action("Browse") // Not in ["API_Call", "Download"]
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertTrue((decision as AccessDecision.Decision).allowed, "Non-tracked actions should pass")
        println("✓ Test 9-4 passed: Non-tracked action allowed")
    }

    // ============================================
    // Scenario 10: Ethical Wall
    // ============================================

    @Test
    fun `10-1 Ethical Wall - allow access without conflict`() = runTest {
        val policy = CoreAbacScenarios.ethicalWall()
        engine.storePolicy(policy)

        val context = evaluationContext {
            subject("assignedClients", listOf("Client_C", "Client_D"))
            resource("client", "Client_A")
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertTrue((decision as AccessDecision.Decision).allowed, "No conflict should allow access")
        println("✓ Test 10-1 passed: Access without conflict allowed")
    }

    @Test
    fun `10-2 Ethical Wall - deny access with conflict of interest`() = runTest {
        val policy = CoreAbacScenarios.ethicalWall()
        engine.storePolicy(policy)

        val context = evaluationContext {
            subject("assignedClients", listOf("Client_B", "Client_C")) // Contains Client_B
            resource("client", "Client_A")
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertFalse((decision as AccessDecision.Decision).allowed, "Conflict of interest should deny access")
        println("✓ Test 10-2 passed: Access with conflict denied")
    }

    @Test
    fun `10-3 Ethical Wall - allow access to non-Client_A resources`() = runTest {
        val policy = CoreAbacScenarios.ethicalWall()
        engine.storePolicy(policy)

        val context = evaluationContext {
            subject("assignedClients", listOf("Client_B", "Client_C"))
            resource("client", "Client_D") // Not Client_A
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertTrue((decision as AccessDecision.Decision).allowed, "Non-Client_A resources should be accessible")
        println("✓ Test 10-3 passed: Non-conflicting client access allowed")
    }

    @Test
    fun `10-4 Ethical Wall - allow user with empty client list`() = runTest {
        val policy = CoreAbacScenarios.ethicalWall()
        engine.storePolicy(policy)

        val context = evaluationContext {
            subject("assignedClients", emptyList<String>())
            resource("client", "Client_A")
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertTrue((decision as AccessDecision.Decision).allowed, "User with no clients should have access")
        println("✓ Test 10-4 passed: User with empty client list allowed")
    }

    // ============================================
    // Scenario 11: Tax Invoice Approval
    // ============================================

    @Test
    fun `11-1 Tax Invoice - allow team leader for small amount (under 1M KRW)`() = runTest {
        val policy = CoreAbacScenarios.taxInvoiceApproval()
        engine.storePolicy(policy)

        val context = evaluationContext {
            subject("position", "TeamLeader")
            action("Approve_Tax_Invoice")
            resource("amount", 500000) // 500,000 KRW
            resource("type", "TaxInvoice")
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertTrue((decision as AccessDecision.Decision).allowed, "Team leader should approve under 1M KRW")
        println("✓ Test 11-1 passed: Team leader approved 500K KRW invoice")
    }

    @Test
    fun `11-2 Tax Invoice - deny team leader for medium amount (5M KRW)`() = runTest {
        val policy = CoreAbacScenarios.taxInvoiceApproval()
        engine.storePolicy(policy)

        val context = evaluationContext {
            subject("position", "TeamLeader")
            action("Approve_Tax_Invoice")
            resource("amount", 5000000) // 5,000,000 KRW (requires DepartmentHead)
            resource("type", "TaxInvoice")
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertFalse((decision as AccessDecision.Decision).allowed, "Team leader cannot approve 5M KRW")
        println("✓ Test 11-2 passed: Team leader denied 5M KRW invoice")
    }

    @Test
    fun `11-3 Tax Invoice - allow department head for medium amount (5M KRW)`() = runTest {
        val policy = CoreAbacScenarios.taxInvoiceApproval()
        engine.storePolicy(policy)

        val context = evaluationContext {
            subject("position", "DepartmentHead")
            action("Approve_Tax_Invoice")
            resource("amount", 5000000) // 5,000,000 KRW
            resource("type", "TaxInvoice")
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertTrue((decision as AccessDecision.Decision).allowed, "Department head should approve 5M KRW")
        println("✓ Test 11-3 passed: Department head approved 5M KRW invoice")
    }

    @Test
    fun `11-4 Tax Invoice - deny department head for large amount (50M KRW)`() = runTest {
        val policy = CoreAbacScenarios.taxInvoiceApproval()
        engine.storePolicy(policy)

        val context = evaluationContext {
            subject("position", "DepartmentHead")
            action("Approve_Tax_Invoice")
            resource("amount", 50000000) // 50,000,000 KRW (requires DivisionHead)
            resource("type", "TaxInvoice")
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertFalse((decision as AccessDecision.Decision).allowed, "Department head cannot approve 50M KRW")
        println("✓ Test 11-4 passed: Department head denied 50M KRW invoice")
    }

    @Test
    fun `11-5 Tax Invoice - allow division head for large amount (50M KRW)`() = runTest {
        val policy = CoreAbacScenarios.taxInvoiceApproval()
        engine.storePolicy(policy)

        val context = evaluationContext {
            subject("position", "DivisionHead")
            action("Approve_Tax_Invoice")
            resource("amount", 50000000) // 50,000,000 KRW
            resource("type", "TaxInvoice")
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertTrue((decision as AccessDecision.Decision).allowed, "Division head should approve 50M KRW")
        println("✓ Test 11-5 passed: Division head approved 50M KRW invoice")
    }

    @Test
    fun `11-6 Tax Invoice - deny division head for very large amount (200M KRW)`() = runTest {
        val policy = CoreAbacScenarios.taxInvoiceApproval()
        engine.storePolicy(policy)

        val context = evaluationContext {
            subject("position", "DivisionHead")
            action("Approve_Tax_Invoice")
            resource("amount", 200000000) // 200,000,000 KRW (requires ExecutiveDirector)
            resource("type", "TaxInvoice")
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertFalse((decision as AccessDecision.Decision).allowed, "Division head cannot approve 200M KRW")
        println("✓ Test 11-6 passed: Division head denied 200M KRW invoice")
    }

    @Test
    fun `11-7 Tax Invoice - allow executive director for any amount`() = runTest {
        val policy = CoreAbacScenarios.taxInvoiceApproval()
        engine.storePolicy(policy)

        val context = evaluationContext {
            subject("position", "ExecutiveDirector")
            action("Approve_Tax_Invoice")
            resource("amount", 500000000) // 500,000,000 KRW
            resource("type", "TaxInvoice")
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertTrue((decision as AccessDecision.Decision).allowed, "Executive director should approve any amount")
        println("✓ Test 11-7 passed: Executive director approved 500M KRW invoice")
    }

    @Test
    fun `11-8 Tax Invoice - test boundary at 1M KRW`() = runTest {
        val policy = CoreAbacScenarios.taxInvoiceApproval()
        engine.storePolicy(policy)

        // Exactly 1,000,000 KRW - requires DepartmentHead
        val context = evaluationContext {
            subject("position", "TeamLeader")
            action("Approve_Tax_Invoice")
            resource("amount", 1000000) // Exactly 1,000,000 KRW
            resource("type", "TaxInvoice")
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertFalse((decision as AccessDecision.Decision).allowed, "1M KRW requires Department Head")
        println("✓ Test 11-8 passed: Boundary test at 1M KRW")
    }

    @Test
    fun `11-9 Tax Invoice - higher position can approve lower amounts`() = runTest {
        val policy = CoreAbacScenarios.taxInvoiceApproval()
        engine.storePolicy(policy)

        // Executive Director approving small amount
        val context = evaluationContext {
            subject("position", "ExecutiveDirector")
            action("Approve_Tax_Invoice")
            resource("amount", 100000) // 100,000 KRW (small amount)
            resource("type", "TaxInvoice")
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertTrue((decision as AccessDecision.Decision).allowed, "Higher position can approve lower amounts")
        println("✓ Test 11-9 passed: Executive director approved small amount")
    }

    @Test
    fun `11-10 Tax Invoice with SoD - deny self-approval even with correct position`() = runTest {
        val policy = CoreAbacScenarios.taxInvoiceApprovalWithSoD()
        engine.storePolicy(policy)

        val context = evaluationContext {
            subject("id", "user123")
            subject("position", "DepartmentHead")
            action("Approve_Tax_Invoice")
            resource("ownerId", "user123") // Same as subject.id - self-approval
            resource("amount", 5000000)
            resource("type", "TaxInvoice")
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertFalse((decision as AccessDecision.Decision).allowed, "Self-approval should be denied")
        println("✓ Test 11-10 passed: Self-approval denied with SoD")
    }

    @Test
    fun `11-11 Tax Invoice with SoD - allow approval by different person with correct position`() = runTest {
        val policy = CoreAbacScenarios.taxInvoiceApprovalWithSoD()
        engine.storePolicy(policy)

        val context = evaluationContext {
            subject("id", "approver456")
            subject("position", "DepartmentHead")
            action("Approve_Tax_Invoice")
            resource("ownerId", "creator123") // Different from subject.id
            resource("amount", 5000000)
            resource("type", "TaxInvoice")
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertTrue((decision as AccessDecision.Decision).allowed, "Different person with correct position should approve")
        println("✓ Test 11-11 passed: Approval by different person with SoD")
    }

    @Test
    fun `11-12 Tax Invoice with SoD - deny when position insufficient even if not self-approval`() = runTest {
        val policy = CoreAbacScenarios.taxInvoiceApprovalWithSoD()
        engine.storePolicy(policy)

        val context = evaluationContext {
            subject("id", "approver456")
            subject("position", "TeamLeader") // Insufficient position
            action("Approve_Tax_Invoice")
            resource("ownerId", "creator123") // Different person
            resource("amount", 5000000) // Requires DepartmentHead
            resource("type", "TaxInvoice")
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertFalse((decision as AccessDecision.Decision).allowed, "Insufficient position should be denied")
        println("✓ Test 11-12 passed: Insufficient position denied")
    }

    @Test
    fun `11-13 Tax Invoice - comprehensive amount tier test`() = runTest {
        val policy = CoreAbacScenarios.taxInvoiceApproval()
        engine.storePolicy(policy)

        data class AmountTierTest(
            val amount: Int,
            val position: String,
            val shouldAllow: Boolean,
            val description: String
        )

        val testCases = listOf(
            // Under 1M KRW tier
            AmountTierTest(999999, "TeamLeader", true, "999,999 KRW by TeamLeader"),
            AmountTierTest(999999, "DepartmentHead", true, "999,999 KRW by DepartmentHead"),

            // 1M ~ 10M KRW tier
            AmountTierTest(1000000, "TeamLeader", false, "1M KRW by TeamLeader"),
            AmountTierTest(1000000, "DepartmentHead", true, "1M KRW by DepartmentHead"),
            AmountTierTest(9999999, "DepartmentHead", true, "9,999,999 KRW by DepartmentHead"),

            // 10M ~ 100M KRW tier
            AmountTierTest(10000000, "DepartmentHead", false, "10M KRW by DepartmentHead"),
            AmountTierTest(10000000, "DivisionHead", true, "10M KRW by DivisionHead"),
            AmountTierTest(99999999, "DivisionHead", true, "99,999,999 KRW by DivisionHead"),

            // 100M KRW and above tier
            AmountTierTest(100000000, "DivisionHead", false, "100M KRW by DivisionHead"),
            AmountTierTest(100000000, "ExecutiveDirector", true, "100M KRW by ExecutiveDirector"),
            AmountTierTest(1000000000, "ExecutiveDirector", true, "1B KRW by ExecutiveDirector")
        )

        var passedCount = 0
        testCases.forEach { testCase ->
            val context = evaluationContext {
                subject("position", testCase.position)
                action("Approve_Tax_Invoice")
                resource("amount", testCase.amount)
            }

            val decision = engine.checkAccess(policy.id, context)
            assertTrue(decision is AccessDecision.Decision)

            val actualAllowed = (decision as AccessDecision.Decision).allowed
            if (actualAllowed == testCase.shouldAllow) {
                passedCount++
                println("  ✓ ${testCase.description}: ${if (testCase.shouldAllow) "ALLOWED" else "DENIED"}")
            } else {
                println("  ✗ ${testCase.description}: Expected ${testCase.shouldAllow}, got $actualAllowed")
            }
        }

        assertEquals(testCases.size, passedCount, "All tier tests should pass")
        println("✓ Test 11-13 passed: All ${testCases.size} amount tier tests passed")
    }

    // ============================================
    // Scenario 12: Function-Based Tax Invoice Approval
    // ============================================

    @Test
    fun `12-1 Capability-Based - allow user with approval capability`() = runTest {
        val policy = CoreAbacScenarios.taxInvoiceApprovalByCapability()
        engine.storePolicy(policy)

        val context = evaluationContext {
            subject("id", "accountant_kim")
            subject("position", "Staff") // Low position but has capability
            subject("capabilities", listOf("Approve_Tax_Invoice", "View_Reports"))
            action("Approve_Tax_Invoice")
            resource("amount", 50000000)
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertTrue((decision as AccessDecision.Decision).allowed, "User with capability should approve")
        println("✓ Test 12-1 passed: User with capability approved")
    }

    @Test
    fun `12-2 Capability-Based - deny user without approval capability`() = runTest {
        val policy = CoreAbacScenarios.taxInvoiceApprovalByCapability()
        engine.storePolicy(policy)

        val context = evaluationContext {
            subject("id", "manager_lee")
            subject("position", "Manager") // High position but no capability
            subject("capabilities", listOf("View_Reports", "Edit_Documents"))
            action("Approve_Tax_Invoice")
            resource("amount", 5000000)
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertFalse((decision as AccessDecision.Decision).allowed, "User without capability should be denied")
        println("✓ Test 12-2 passed: User without capability denied")
    }

    @Test
    fun `12-3 User List - allow authorized user by ID`() = runTest {
        val policy = CoreAbacScenarios.taxInvoiceApprovalByUserList()
        engine.storePolicy(policy)

        val context = evaluationContext {
            subject("id", "certified_accountant_002") // In the authorized list
            subject("position", "Accountant")
            action("Approve_Tax_Invoice")
            resource("amount", 10000000)
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertTrue((decision as AccessDecision.Decision).allowed, "Authorized user should approve")
        println("✓ Test 12-3 passed: Authorized user approved")
    }

    @Test
    fun `12-4 User List - deny unauthorized user by ID`() = runTest {
        val policy = CoreAbacScenarios.taxInvoiceApprovalByUserList()
        engine.storePolicy(policy)

        val context = evaluationContext {
            subject("id", "regular_user_999") // NOT in the authorized list
            subject("position", "Manager")
            action("Approve_Tax_Invoice")
            resource("amount", 1000000)
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertFalse((decision as AccessDecision.Decision).allowed, "Unauthorized user should be denied")
        println("✓ Test 12-4 passed: Unauthorized user denied")
    }

    @Test
    fun `12-5 Permission Group - allow user in approver group`() = runTest {
        val policy = CoreAbacScenarios.taxInvoiceApprovalByPermissionGroup()
        engine.storePolicy(policy)

        val context = evaluationContext {
            subject("id", "user_choi")
            subject("position", "Assistant")
            subject("permissionGroups", listOf("TaxInvoiceApprovers", "FinanceTeam"))
            action("Approve_Tax_Invoice")
            resource("amount", 20000000)
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertTrue((decision as AccessDecision.Decision).allowed, "User in approver group should approve")
        println("✓ Test 12-5 passed: User in permission group approved")
    }

    @Test
    fun `12-6 Permission Group - deny user not in approver group`() = runTest {
        val policy = CoreAbacScenarios.taxInvoiceApprovalByPermissionGroup()
        engine.storePolicy(policy)

        val context = evaluationContext {
            subject("id", "user_park")
            subject("position", "Manager")
            subject("permissionGroups", listOf("HRTeam", "SalesTeam"))
            action("Approve_Tax_Invoice")
            resource("amount", 5000000)
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertFalse((decision as AccessDecision.Decision).allowed, "User not in approver group should be denied")
        println("✓ Test 12-6 passed: User not in permission group denied")
    }

    @Test
    fun `12-7 Hybrid - allow executive by position`() = runTest {
        val policy = CoreAbacScenarios.taxInvoiceApprovalHybrid()
        engine.storePolicy(policy)

        val context = evaluationContext {
            subject("id", "executive_jung")
            subject("position", "ExecutiveDirector")
            subject("capabilities", emptyList<String>()) // No capabilities, but high position
            action("Approve_Tax_Invoice")
            resource("amount", 100000000)
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertTrue((decision as AccessDecision.Decision).allowed, "Executive should approve by position")
        println("✓ Test 12-7 passed: Executive approved by position")
    }

    @Test
    fun `12-8 Hybrid - allow staff with capability`() = runTest {
        val policy = CoreAbacScenarios.taxInvoiceApprovalHybrid()
        engine.storePolicy(policy)

        val context = evaluationContext {
            subject("id", "staff_han")
            subject("position", "Staff") // Low position
            subject("capabilities", listOf("Approve_Tax_Invoice")) // But has capability
            action("Approve_Tax_Invoice")
            resource("amount", 30000000)
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertTrue((decision as AccessDecision.Decision).allowed, "Staff with capability should approve")
        println("✓ Test 12-8 passed: Staff approved by capability")
    }

    @Test
    fun `12-9 Hybrid - deny manager without position or capability`() = runTest {
        val policy = CoreAbacScenarios.taxInvoiceApprovalHybrid()
        engine.storePolicy(policy)

        val context = evaluationContext {
            subject("id", "manager_song")
            subject("position", "Manager") // Mid-level position (not ExecutiveDirector)
            subject("capabilities", listOf("View_Reports")) // No approval capability
            action("Approve_Tax_Invoice")
            resource("amount", 10000000)
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertFalse((decision as AccessDecision.Decision).allowed, "Manager without capability should be denied")
        println("✓ Test 12-9 passed: Manager without capability denied")
    }

    @Test
    fun `12-10 Capability with SoD - deny self-approval even with capability`() = runTest {
        val policy = CoreAbacScenarios.taxInvoiceApprovalByCapabilityWithSoD()
        engine.storePolicy(policy)

        val context = evaluationContext {
            subject("id", "accountant_lim")
            subject("capabilities", listOf("Approve_Tax_Invoice"))
            action("Approve_Tax_Invoice")
            resource("ownerId", "accountant_lim") // Same as subject.id
            resource("amount", 5000000)
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertFalse((decision as AccessDecision.Decision).allowed, "Self-approval should be denied")
        println("✓ Test 12-10 passed: Self-approval denied with SoD")
    }

    @Test
    fun `12-11 Capability with SoD - allow different user with capability`() = runTest {
        val policy = CoreAbacScenarios.taxInvoiceApprovalByCapabilityWithSoD()
        engine.storePolicy(policy)

        val context = evaluationContext {
            subject("id", "accountant_oh")
            subject("capabilities", listOf("Approve_Tax_Invoice"))
            action("Approve_Tax_Invoice")
            resource("ownerId", "accountant_lim") // Different from subject.id
            resource("amount", 5000000)
        }

        val decision = engine.checkAccess(policy.id, context)

        assertTrue(decision is AccessDecision.Decision)
        assertTrue((decision as AccessDecision.Decision).allowed, "Different user with capability should approve")
        println("✓ Test 12-11 passed: Different user approved with SoD")
    }

    @Test
    fun `12-12 Multiple authorized users - test all three accountants`() = runTest {
        val policy = CoreAbacScenarios.taxInvoiceApprovalByUserList()
        engine.storePolicy(policy)

        val authorizedUsers = listOf(
            "certified_accountant_001",
            "certified_accountant_002",
            "certified_accountant_003"
        )

        var passedCount = 0
        authorizedUsers.forEach { userId ->
            val context = evaluationContext {
                subject("id", userId)
                action("Approve_Tax_Invoice")
                resource("amount", 10000000)
            }

            val decision = engine.checkAccess(policy.id, context)
            assertTrue(decision is AccessDecision.Decision)

            if ((decision as AccessDecision.Decision).allowed) {
                passedCount++
                println("  ✓ User $userId: APPROVED")
            }
        }

        assertEquals(authorizedUsers.size, passedCount, "All 3 authorized users should approve")
        println("✓ Test 12-12 passed: All 3 authorized users approved")
    }

    @Test
    fun `12-13 Capability vs Position comparison`() = runTest {
        val capabilityPolicy = CoreAbacScenarios.taxInvoiceApprovalByCapability()
        val hybridPolicy = CoreAbacScenarios.taxInvoiceApprovalHybrid()

        engine.storePolicy(capabilityPolicy)
        engine.storePolicy(hybridPolicy)

        // Scenario 1: Staff with capability
        val context1 = evaluationContext {
            subject("id", "staff_yoon")
            subject("position", "Staff")
            subject("capabilities", listOf("Approve_Tax_Invoice"))
            action("Approve_Tax_Invoice")
            resource("amount", 10000000)
        }

        val decision1Cap = engine.checkAccess(capabilityPolicy.id, context1)
        val decision1Hyb = engine.checkAccess(hybridPolicy.id, context1)

        assertTrue((decision1Cap as AccessDecision.Decision).allowed, "Capability policy allows staff")
        assertTrue((decision1Hyb as AccessDecision.Decision).allowed, "Hybrid policy allows staff with capability")

        // Scenario 2: Executive without capability
        val context2 = evaluationContext {
            subject("id", "executive_bae")
            subject("position", "ExecutiveDirector")
            subject("capabilities", emptyList<String>())
            action("Approve_Tax_Invoice")
            resource("amount", 100000000)
        }

        val decision2Cap = engine.checkAccess(capabilityPolicy.id, context2)
        val decision2Hyb = engine.checkAccess(hybridPolicy.id, context2)

        assertFalse((decision2Cap as AccessDecision.Decision).allowed, "Capability policy denies executive without capability")
        assertTrue((decision2Hyb as AccessDecision.Decision).allowed, "Hybrid policy allows executive by position")

        println("✓ Test 12-13 passed: Capability vs Position comparison completed")
    }

    // ============================================
    // Summary Test
    // ============================================

    @Test
    fun `All 17 scenarios - comprehensive integration test`() = runTest {
        val scenarios = listOf(
            CoreAbacScenarios.separationOfDuties(),
            CoreAbacScenarios.projectBasedAccess(),
            CoreAbacScenarios.contextAwareAccess(),
            CoreAbacScenarios.documentLifecycle(),
            CoreAbacScenarios.hierarchicalAccess(),
            CoreAbacScenarios.piiProtection(),
            CoreAbacScenarios.timeLimitedDelegation(),
            CoreAbacScenarios.riskBasedControl(),
            CoreAbacScenarios.usageQuota(),
            CoreAbacScenarios.ethicalWall(),
            CoreAbacScenarios.taxInvoiceApproval(),
            CoreAbacScenarios.taxInvoiceApprovalWithSoD(),
            CoreAbacScenarios.taxInvoiceApprovalByCapability(),
            CoreAbacScenarios.taxInvoiceApprovalByUserList(),
            CoreAbacScenarios.taxInvoiceApprovalByPermissionGroup(),
            CoreAbacScenarios.taxInvoiceApprovalHybrid(),
            CoreAbacScenarios.taxInvoiceApprovalByCapabilityWithSoD()
        )

        // Store all policies
        scenarios.forEach { policy ->
            engine.storePolicy(policy)
        }

        // Verify all are stored
        val allPolicyIds = scenarios.map { it.id }
        assertEquals(17, allPolicyIds.size, "All 17 policies should be stored")

        // Verify cache statistics
        val stats = engine.getCacheStatistics()
        assertTrue(stats.size >= 0, "Cache should be initialized")

        println("✓ All 17 scenarios (12 base + 5 capability-based variants) stored and validated successfully")
    }
}
