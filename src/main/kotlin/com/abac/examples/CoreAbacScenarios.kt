package com.abac.examples

import com.abac.dsl.*
import com.abac.model.*

/**
 * Core 10 ABAC scenarios demonstrating fundamental access control patterns
 * These scenarios cover the most common enterprise use cases
 */
object CoreAbacScenarios {

    /**
     * Scenario 1: Separation of Duties (SoD)
     *
     * Prevents self-approval to maintain internal controls
     * Key: Compares user.id with resource.ownerId
     */
    fun separationOfDuties() = policy("SoD Policy", "sod_policy") {
        (action() eq "Approve") implies (
            (subject.role eq "Manager") and (resource.ownerId neq subject.id)
        )
    }

    /**
     * Scenario 2: Project-Based Dynamic Access Control
     *
     * Users can only access projects they're assigned to
     * Key: Collection membership check (IN operation)
     */
    fun projectBasedAccess() = policy("Project Match", "project_match") {
        (action() isIn listOf("Read", "Edit")) implies (
            resource["projectId"] isIn subject["activeProjects"]
        )
    }

    /**
     * Scenario 3: Environment and Location-Based Security
     *
     * Top secret resources require corporate network and device
     * Key: Environment object reference and multiple conditions
     */
    fun contextAwareAccess() = policy("Top Secret Guard", "top_secret_guard") {
        (resource["classification"] eq "Top Secret") implies (
            (subject["location"] eq "Office_Network") and
            (Expression.Variable("environment.deviceType") eq const("Corporate_Laptop"))
        )
    }

    /**
     * Scenario 4: Resource Lifecycle-Based Access
     *
     * Different permissions based on document status
     * Key: Pattern matching with whenCase
     */
    fun documentLifecycle() = policy("Document Lifecycle", "document_lifecycle") {
        whenCase(resource.status) {
            (resource.status eq "Draft") then (subject.id eq resource.creatorId)
            (resource.status eq "Under_Review") then (subject.role eq "Editor")
            (resource.status eq "Published") then (subject.role eq "Public")
            elseCase(false) // Default deny
        }
    }

    /**
     * Scenario 5: Hierarchical Access
     *
     * Managers can view subordinates' performance reviews
     * Key: Nested object navigation (resource.owner.managerId)
     */
    fun hierarchicalAccess() = policy("Manager View", "manager_view") {
        (action() eq "View_Performance_Review") implies (
            subject.id eq resource.owner.managerId
        )
    }

    /**
     * Scenario 6: Purpose-Based PII Access (GDPR)
     *
     * PII data requires specific purpose justification
     * Key: Action object properties and purpose validation
     */
    fun piiProtection() = policy("PII Protection", "pii_protection") {
        (resource.type eq "PII_Data") implies (
            (subject.role eq "HR") and
            (Expression.Variable("action.purpose") eq const("Salary_Processing"))
        )
    }

    /**
     * Scenario 7: Time-Limited Delegation
     *
     * Temporary approval rights with expiration
     * Key: Time range comparison with between function
     */
    fun timeLimitedDelegation() = policy("Delegation", "delegation") {
        (action() eq "Approve") implies (
            (subject.id eq resource.delegatedTo) and
            between(
                currentTime(),
                Expression.Variable("resource.delegation.startTime"),
                Expression.Variable("resource.delegation.endTime")
            )
        )
    }

    /**
     * Scenario 8: Risk Score-Based Access Control
     *
     * High-risk operations require low risk score
     * Key: Numeric comparison (less than)
     */
    fun riskBasedControl() = policy("Risk Control", "risk_control") {
        (action() eq "Delete_Table") implies (
            (subject.role eq "Admin") and (subject.riskScore lt 30)
        )
    }

    /**
     * Scenario 9: Resource Quota and Rate Limiting
     *
     * Users have daily usage limits based on subscription level
     * Key: Counter value comparison
     */
    fun usageQuota() = policy("Usage Quota", "usage_quota") {
        (action() isIn listOf("API_Call", "Download")) implies (
            (subject.subLevel eq "Basic") and (subject.dailyUsage lt 100)
        )
    }

    /**
     * Scenario 10: Ethical Wall (Conflict of Interest)
     *
     * Prevents users from accessing conflicting client data
     * Key: CONTAINS operation with NOT logic
     */
    fun ethicalWall() = policy("Ethical Wall", "ethical_wall") {
        (resource.client eq "Client_A") implies (
            not(subject.assignedClients contains "Client_B")
        )
    }

    /**
     * Scenario 11: Tax Invoice Approval by Position and Amount
     *
     * Korean enterprise approval workflow for tax invoice issuance
     * Amount-based approval authority:
     * - Under 1M KRW: Team Leader (팀장)
     * - 1M ~ 10M KRW: Department Head (실장)
     * - 10M ~ 100M KRW: Division Head (본부장)
     * - 100M KRW and above: Executive Director (상무)
     *
     * Key: Amount-based position matching with whenCase
     */
    fun taxInvoiceApproval() = policy("Tax Invoice Approval", "tax_invoice_approval") {
        (action() eq "Approve_Tax_Invoice") implies
        whenCase(resource["amount"]) {
            // Under 1,000,000 KRW - Team Leader
            (resource["amount"] lt 1000000) then (
                (subject["position"] eq "TeamLeader") or
                (subject["position"] eq "DepartmentHead") or
                (subject["position"] eq "DivisionHead") or
                (subject["position"] eq "ExecutiveDirector")
            )
            // 1M ~ 10M KRW - Department Head or higher
            (resource["amount"] lt 10000000) then (
                (subject["position"] eq "DepartmentHead") or
                (subject["position"] eq "DivisionHead") or
                (subject["position"] eq "ExecutiveDirector")
            )
            // 10M ~ 100M KRW - Division Head or higher
            (resource["amount"] lt 100000000) then (
                (subject["position"] eq "DivisionHead") or
                (subject["position"] eq "ExecutiveDirector")
            )
            // 100M KRW and above - Executive Director only
            (resource["amount"] gte 100000000) then (
                subject["position"] eq "ExecutiveDirector"
            )
            // Default deny
            elseCase(false)
        }
    }

    /**
     * Scenario 11-B: Tax Invoice Approval with SoD (Separation of Duties)
     *
     * Same as Scenario 11 but also prevents self-approval
     * Combines amount-based approval with SoD principle
     */
    fun taxInvoiceApprovalWithSoD() = policy("Tax Invoice Approval with SoD", "tax_invoice_approval_sod") {
        (action() eq "Approve_Tax_Invoice") implies (
            // Must not be the creator
            (resource.ownerId neq subject.id) and
            // Amount-based position check
            whenCase(resource["amount"]) {
                (resource["amount"] lt 1000000) then (
                    subject["position"] isIn listOf("TeamLeader", "DepartmentHead", "DivisionHead", "ExecutiveDirector")
                )
                (resource["amount"] lt 10000000) then (
                    subject["position"] isIn listOf("DepartmentHead", "DivisionHead", "ExecutiveDirector")
                )
                (resource["amount"] lt 100000000) then (
                    subject["position"] isIn listOf("DivisionHead", "ExecutiveDirector")
                )
                (resource["amount"] gte 100000000) then (
                    subject["position"] eq "ExecutiveDirector"
                )
                elseCase(false)
            }
        )
    }

    /**
     * Scenario 12: Function-Based Tax Invoice Approval (Capability-Based Access)
     *
     * Unlike Scenario 11 (position hierarchy), this scenario grants approval rights
     * based on specific user capabilities, not organizational hierarchy.
     *
     * Key differences from hierarchical approach:
     * - Permission is granted to specific users, not positions
     * - Multiple users can have same approval capability
     * - No amount tiers - all authorized users have equal approval rights
     * - Represents functional/capability-based access control
     *
     * Real-world use case:
     * - Tax invoice approval authority granted to certified accountants
     * - Regardless of their position, they have approval capability
     * - Three specific users have been granted this capability
     *
     * Key: User capability check and subject identifier validation
     */
    fun taxInvoiceApprovalByCapability() = policy("Tax Invoice Approval by Capability", "tax_invoice_approval_capability") {
        (action() eq "Approve_Tax_Invoice") implies (
            // Check if user has the specific capability
            subject["capabilities"] contains "Approve_Tax_Invoice"
        )
    }

    /**
     * Scenario 12-B: Function-Based Approval with Explicit User List
     *
     * Alternative approach: explicitly list authorized users
     * Useful when capability system is not available
     */
    fun taxInvoiceApprovalByUserList() = policy("Tax Invoice Approval by User List", "tax_invoice_approval_userlist") {
        (action() eq "Approve_Tax_Invoice") implies (
            // Only specific users can approve
            subject.id isIn listOf("certified_accountant_001", "certified_accountant_002", "certified_accountant_003")
        )
    }

    /**
     * Scenario 12-C: Function-Based Approval with Permission Group
     *
     * Uses permission groups for flexible access management
     * Allows adding/removing users from group without policy changes
     */
    fun taxInvoiceApprovalByPermissionGroup() = policy("Tax Invoice Approval by Permission Group", "tax_invoice_approval_group") {
        (action() eq "Approve_Tax_Invoice") implies (
            // Check if user belongs to the approver group
            subject["permissionGroups"] contains "TaxInvoiceApprovers"
        )
    }

    /**
     * Scenario 12-D: Hybrid Approach - Position OR Capability
     *
     * Combines hierarchical and capability-based access
     * Executives can approve by position, others need specific capability
     */
    fun taxInvoiceApprovalHybrid() = policy("Tax Invoice Approval Hybrid", "tax_invoice_approval_hybrid") {
        (action() eq "Approve_Tax_Invoice") implies (
            // Either high-level position OR specific capability
            (subject["position"] eq "ExecutiveDirector") or
            (subject["capabilities"] contains "Approve_Tax_Invoice")
        )
    }

    /**
     * Scenario 12-E: Capability-Based with SoD
     *
     * Combines capability-based access with separation of duties
     * Users must have capability AND not be the creator
     */
    fun taxInvoiceApprovalByCapabilityWithSoD() = policy("Tax Invoice Approval by Capability with SoD", "tax_invoice_approval_capability_sod") {
        (action() eq "Approve_Tax_Invoice") implies (
            // Must have capability
            (subject["capabilities"] contains "Approve_Tax_Invoice") and
            // Must not be the creator (SoD)
            (resource.ownerId neq subject.id)
        )
    }
}
