package sg.nus.carelink.profile.infrastructure.persistence.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for table elder_primary_caregiver (V9). Keyed by elder_id, so an elder has at most
 * one primary caregiver; references are plain ids, as everywhere else in the schema.
 */
@Entity
@Table(name = "elder_primary_caregiver")
public class PrimaryCaregiverAssignmentJpaEntity {

	@Id
	@Column(name = "elder_id")
	private Long elderId;

	@Column(name = "caregiver_id", nullable = false)
	private Long caregiverId;

	@Column(name = "assigned_at", nullable = false)
	private LocalDateTime assignedAt;

	public PrimaryCaregiverAssignmentJpaEntity() {
	}

	public Long getElderId() {
		return elderId;
	}

	public void setElderId(Long elderId) {
		this.elderId = elderId;
	}

	public Long getCaregiverId() {
		return caregiverId;
	}

	public void setCaregiverId(Long caregiverId) {
		this.caregiverId = caregiverId;
	}

	public LocalDateTime getAssignedAt() {
		return assignedAt;
	}

	public void setAssignedAt(LocalDateTime assignedAt) {
		this.assignedAt = assignedAt;
	}
}
