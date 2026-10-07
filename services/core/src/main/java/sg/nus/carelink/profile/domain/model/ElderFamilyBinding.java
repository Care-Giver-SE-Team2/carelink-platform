package sg.nus.carelink.profile.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;

import sg.nus.carelink.shared.error.BusinessRuleViolation;

/**
 * Domain model for the relationship between an elder and a family member.
 *
 * <p>The elder initiates the binding. A newly requested binding starts in
 * PENDING_CONFIRMATION. Confirmation by the family member belongs to the
 * family-side workflow and is outside the elder-side EL04 implementation.
 */
public record ElderFamilyBinding(
        Long id,
        Long elderId,
        Long familyMemberId,
        Relationship relationship,
        boolean isPrimaryContact,
        AccessScope accessScope,
        Status status,
        LocalDateTime confirmedAt,
        LocalDateTime expiresAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

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

    /**
     * Creates a new elder-initiated family binding request.
     */
    public static ElderFamilyBinding request(
            Long elderId,
            Long familyMemberId,
            Relationship relationship,
            boolean primaryContact,
            AccessScope accessScope) {

        if (elderId == null) {
            throw new IllegalArgumentException(
                    "elderId is required"
            );
        }

        if (familyMemberId == null) {
            throw new IllegalArgumentException(
                    "familyMemberId is required"
            );
        }

        if (relationship == null) {
            throw new IllegalArgumentException(
                    "relationship is required"
            );
        }

        if (accessScope == null) {
            throw new IllegalArgumentException(
                    "accessScope is required"
            );
        }

        return new ElderFamilyBinding(
                null,
                elderId,
                familyMemberId,
                relationship,
                primaryContact,
                accessScope,
                Status.PENDING_CONFIRMATION,
                null,
                null,
                null,
                null
        );
    }

    /**
     * Re-opens a previously rejected or revoked relationship.
     *
     * <p>The database has a unique elder/family pair, so a new row cannot be
     * inserted for the same pair. Instead the existing row becomes pending
     * again.
     */
    public ElderFamilyBinding requestAgain(
            Relationship newRelationship,
            boolean primaryContact,
            AccessScope newAccessScope) {

        if (status != Status.REJECTED
                && status != Status.REVOKED) {

            throw new BusinessRuleViolation(
                    "FAMILY_BINDING_ALREADY_EXISTS",
                    "This family member is already linked or awaiting confirmation."
            );
        }

        return new ElderFamilyBinding(
                id,
                elderId,
                familyMemberId,
                newRelationship,
                primaryContact,
                newAccessScope,
                Status.PENDING_CONFIRMATION,
                null,
                null,
                createdAt,
                updatedAt
        );
    }

    /**
     * Revokes a binding from the elder side.
     */
    public ElderFamilyBinding revoke() {

        if (status == Status.REVOKED) {
            throw new BusinessRuleViolation(
                    "FAMILY_BINDING_ALREADY_REVOKED",
                    "This family binding has already been removed."
            );
        }

        return new ElderFamilyBinding(
                id,
                elderId,
                familyMemberId,
                relationship,
                isPrimaryContact,
                accessScope,
                Status.REVOKED,
                confirmedAt,
                expiresAt,
                createdAt,
                updatedAt
        );
    }

    /**
     * Checks whether this binding grants read access at the supplied Singapore time.
     *
     * @param accessTime Server access time in Asia/Singapore
     * @return True for an ACTIVE, unexpired binding with FULL or READ_ONLY scope
     * @author Wang Zhili
     */
    public boolean allowsReadAt(LocalDateTime accessTime) {
        Objects.requireNonNull(accessTime, "accessTime");
        return status == Status.ACTIVE
                && (accessScope == AccessScope.FULL || accessScope == AccessScope.READ_ONLY)
                && (expiresAt == null || expiresAt.isAfter(accessTime));
    }

    public boolean belongsToElder(Long expectedElderId) {
        return elderId != null
                && elderId.equals(expectedElderId);
    }
}
