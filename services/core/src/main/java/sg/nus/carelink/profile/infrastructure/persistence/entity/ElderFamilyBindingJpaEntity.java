package sg.nus.carelink.profile.infrastructure.persistence.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for table elder_family_binding.
 *
 * Merges elder.family_binding + family.senior_family_links. The elder
 * initiates the binding (team decision 2026-09-05: no pairing code, no
 * requested_by; it is always the elder's request) and the family member
 * confirms. access_scope is what the family reads through afterwards.
 *
 * <p>Generated from V2__care_domain.sql as a starting point; edit freely, it will not
 * be regenerated. Same shape as identity's AppUserJpaEntity: no domain logic here,
 * references to other aggregates are plain ids (DECISION 5 in the schema), so no
 * module depends on another module's persistence classes. The domain model that
 * carries the business rules lives in the module's domain.model package; the mapper
 * between the two is in persistence.adapter.
 *
 * <p>The schema is owned by Flyway. Hibernate validates this mapping at start-up
 * and never alters the table.
 */
@Entity
@Table(name = "elder_family_binding")
public class ElderFamilyBindingJpaEntity {

    public enum Relationship {
        SON,
        DAUGHTER,
        SPOUSE,
        GUARDIAN,
        OTHER
    }

    public enum AccessScope {
        FULL,
        READ_ONLY
    }

    public enum Status {
        PENDING_CONFIRMATION,
        ACTIVE,
        REJECTED,
        REVOKED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "elder_id", nullable = false)
    private Long elderId;

    @Column(name = "family_member_id", nullable = false)
    private Long familyMemberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "relationship", nullable = false)
    private Relationship relationship = Relationship.OTHER;

    @Column(name = "is_primary_contact", nullable = false)
    private boolean isPrimaryContact = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_scope", nullable = false)
    private AccessScope accessScope = AccessScope.FULL;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status = Status.PENDING_CONFIRMATION;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    /**
     * Delegation expiry; revocation cascades to report access.
     */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /**
     * Database-managed creation timestamp.
     *
     * V2__care_domain.sql defines:
     *
     * created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
     *
     * Hibernate therefore does not include this column in INSERT or UPDATE.
     * The repository adapter refreshes the entity after saveAndFlush() so the
     * database-generated value is available to the domain model immediately.
     */
    @Column(
            name = "created_at",
            nullable = false,
            insertable = false,
            updatable = false
    )
    private LocalDateTime createdAt;

    /**
     * Database-managed update timestamp.
     *
     * V2__care_domain.sql defines:
     *
     * updated_at DATETIME NOT NULL
     * DEFAULT CURRENT_TIMESTAMP
     * ON UPDATE CURRENT_TIMESTAMP
     *
     * The repository adapter refreshes the entity after persistence so the
     * latest database-generated value is returned to the application layer.
     */
    @Column(
            name = "updated_at",
            nullable = false,
            insertable = false,
            updatable = false
    )
    private LocalDateTime updatedAt;

    public ElderFamilyBindingJpaEntity() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getElderId() {
        return elderId;
    }

    public void setElderId(Long elderId) {
        this.elderId = elderId;
    }

    public Long getFamilyMemberId() {
        return familyMemberId;
    }

    public void setFamilyMemberId(Long familyMemberId) {
        this.familyMemberId = familyMemberId;
    }

    public Relationship getRelationship() {
        return relationship;
    }

    public void setRelationship(Relationship relationship) {
        this.relationship = relationship;
    }

    public boolean isIsPrimaryContact() {
        return isPrimaryContact;
    }

    public void setIsPrimaryContact(boolean isPrimaryContact) {
        this.isPrimaryContact = isPrimaryContact;
    }

    public AccessScope getAccessScope() {
        return accessScope;
    }

    public void setAccessScope(AccessScope accessScope) {
        this.accessScope = accessScope;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public LocalDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(LocalDateTime confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}