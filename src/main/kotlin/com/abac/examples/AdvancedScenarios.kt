package com.abac.examples

import com.abac.dsl.*
import com.abac.model.*

object AdvancedScenarios {

    /**
     * Scenario 1: Separation of Duties (SoD)
     *
     * Requirement: The creator of an expense report cannot approve it
     * This is a fundamental internal control requirement
     */
    fun separationOfDuties() = policy("Separation of Duties") {
        (action() eq "Approve") and
        (subject("role") eq "Manager") and
        (resource.ownerId neq subject.id)
    }

    /**
     * Scenario 2: Role-Based Access with Department Matching
     *
     * Requirement: Admins can access everything, but managers can only access
     * resources in their own department
     */
    fun roleBasedAccess() = policy("Role Based Access") {
        (subject.role eq "ADMIN") or
        ((subject.role eq "MANAGER") and (resource.department eq subject.department))
    }

    /**
     * Scenario 3: Resource Lifecycle-Based Access Control
     *
     * Requirement: Access permissions change based on document status
     * - Draft: Only creator can edit
     * - Under_Review: Only editors can modify
     * - Published: Public can read
     */
    fun resourceLifecycle() = policy("Document Lifecycle") {
        whenCase(resource.status) {
            (resource.status eq "Draft") then (subject.id eq resource.creatorId)
            (resource.status eq "Under_Review") then (subject.role eq "Editor")
            (resource.status eq "Published") then (subject.role eq "Public")
            elseCase(false) // Default deny
        }
    }

    /**
     * Scenario 4: Hierarchical Access (Manager can view subordinate's performance reviews)
     *
     * Requirement: Managers can view performance reviews of their direct reports
     * Demonstrates nested object navigation (resource.owner.managerId)
     */
    fun hierarchicalAccess() = policy("Manager View Performance") {
        (action() eq "View_Performance_Review") implies
        (subject.id eq resource.owner.managerId)
    }

    /**
     * Scenario 5: Purpose-Based Access for PII (GDPR/Privacy Compliance)
     *
     * Requirement: PII data can only be accessed for legitimate purposes
     * Demonstrates action object properties
     */
    fun purposeBasedAccess() = policy("PII Protection") {
        (resource.type eq "PII_Data") implies (
            (subject.role eq "HR") and
            (Expression.Variable("action.purpose") eq const("Salary_Processing"))
        )
    }

    /**
     * Scenario 6: Time-Limited Delegation
     *
     * Requirement: Temporary approval rights with expiration
     * Demonstrates time comparison and between function
     */
    fun timeLimitedDelegation() = policy("Delegation") {
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
     * Scenario 7: Risk Score-Based Access Control
     *
     * Requirement: High-risk actions require low risk score
     * Demonstrates numeric comparison
     */
    fun riskBasedControl() = policy("Risk Control") {
        (action() eq "Delete_Table") implies (
            (subject.role eq "Admin") and
            (subject.riskScore lt 30)
        )
    }

    /**
     * Scenario 8: Resource Quota and Rate Limiting
     *
     * Requirement: Users have daily usage limits based on subscription level
     */
    fun usageQuota() = policy("Usage Quota") {
        (action() isIn listOf("API_Call", "Download")) implies (
            (subject.subLevel eq "Basic") and
            (subject.dailyUsage lt 100)
        )
    }

    /**
     * Scenario 9: Ethical Wall (Conflict of Interest Prevention)
     *
     * Requirement: Users working with Client A cannot access Client B resources
     * Demonstrates CONTAINS and NOT operators
     */
    fun ethicalWall() = policy("Ethical Wall") {
        (resource.client eq "Client_A") implies
        not(subject.assignedClients contains "Client_B")
    }

    /**
     * Scenario 10: Multi-Factor Context (Time + Location + Device)
     *
     * Requirement: Sensitive operations require business hours, corporate network, and trusted device
     */
    fun multifactorContext() = policy("Multi-Factor Context") {
        (action() eq "Access_Sensitive_Data") implies (
            between(
                Expression.Variable("environment.currentHour"),
                const(9),  // 9 AM
                const(17)  // 5 PM
            ) and
            (Expression.Variable("environment.networkType") eq "Corporate") and
            (Expression.Variable("environment.deviceTrusted") eq true)
        )
    }

    /**
     * Scenario 11: Dynamic Approval Chain
     *
     * Requirement: Approval authority based on amount thresholds
     * - < $1000: Team Lead
     * - $1000-$10000: Manager
     * - > $10000: Director
     */
    fun dynamicApprovalChain() = policy("Approval Chain") {
        (action() eq "Approve_Expense") implies
        whenCase(Expression.Variable("resource.amount")) {
            (resource["amount"] lt 1000) then (subject.role eq "TeamLead")
            (resource["amount"] lt 10000) then (subject.role eq "Manager")
            (resource["amount"] gte 10000) then (subject.role eq "Director")
            elseCase(false)
        }
    }

    /**
     * Scenario 12: Data Classification-Based Access
     *
     * Requirement: Access based on clearance level and data classification
     */
    fun dataClassification() = policy("Data Classification") {
        whenCase(resource["classification"]) {
            (resource["classification"] eq "Public") then const(true)
            (resource["classification"] eq "Internal") then (subject["clearanceLevel"] gte 1)
            (resource["classification"] eq "Confidential") then (subject["clearanceLevel"] gte 2)
            (resource["classification"] eq "Secret") then (subject["clearanceLevel"] gte 3)
            elseCase(false)
        }
    }

    /**
     * Scenario 13: Break-Glass Emergency Access
     *
     * Requirement: Emergency access with audit flag
     * Allows access in emergencies but requires justification
     */
    fun breakGlassAccess() = policy("Break Glass") {
        (Expression.Variable("environment.emergency") eq true) and
        (Expression.Variable("environment.justification").isNotNull())
    }

    /**
     * Scenario 14: Cross-Department Collaboration
     *
     * Requirement: Users can collaborate on shared projects
     * Demonstrates collection operations
     */
    fun crossDepartmentCollaboration() = policy("Cross-Department Collaboration") {
        (resource.type eq "Shared_Project") and (
            (subject.id eq resource.ownerId) or
            (subject.id isIn Expression.Variable("resource.collaborators"))
        )
    }

    /**
     * Scenario 15: Compliance Hold
     *
     * Requirement: Resources under legal hold cannot be deleted
     * Demonstrates negation in real-world scenario
     */
    fun complianceHold() = policy("Compliance Hold") {
        (action() eq "Delete") implies
        not(resource["legalHold"] eq true)
    }
}
