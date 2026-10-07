package sg.nus.carelink.careplan.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

/**
 * JPA entity for table care_plan_required_credential.
 *
 * From the supervisor's screen-2a ERD (CARE_PLAN_REQUIRED_CREDENTIAL). Which
 * certifications a caregiver must hold to be rostered onto this plan; rostering
 * checks it ("certification valid: PASS" on screen 1b).
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
@Table(name = "care_plan_required_credential")
public class CarePlanRequiredCredentialJpaEntity {

	/** Composite key: care_plan_id, credential_type_id. */
	@Embeddable
	public static class Id implements Serializable {

		@Column(name = "care_plan_id", nullable = false)
		private Long carePlanId;

		@Column(name = "credential_type_id", nullable = false)
		private Long credentialTypeId;

		protected Id() {
		}

		public Id(Long carePlanId, Long credentialTypeId) {
			this.carePlanId = carePlanId;
			this.credentialTypeId = credentialTypeId;
		}

		public Long getCarePlanId() {
			return carePlanId;
		}

		public Long getCredentialTypeId() {
			return credentialTypeId;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (!(o instanceof Id other)) {
				return false;
			}
			return Objects.equals(carePlanId, other.carePlanId) && Objects.equals(credentialTypeId, other.credentialTypeId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(carePlanId, credentialTypeId);
		}
	}

	@EmbeddedId
	private Id id;

	public CarePlanRequiredCredentialJpaEntity() {
	}

	public Id getId() {
		return id;
	}

	public void setId(Id id) {
		this.id = id;
	}
}
