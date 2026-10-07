package sg.nus.carelink.careplan.domain.model;

/**
 * Domain model for care_plan_required_credential.
 *
 * <p>Generated starting point: the same fields as the table, and nothing else. This is
 * where the business rules and the design patterns go — reshape it into a proper
 * aggregate (add behaviour, fold child tables in, drop columns the domain does not
 * care about). identity.domain.model.AppUser is the template. Must not import JPA or
 * Spring Data; ArchUnit rejects the build if it does.
 */
public record CarePlanRequiredCredential(
		CarePlanRequiredCredential.Id id) {

	/** Composite key: care_plan_id, credential_type_id. */
	public record Id(Long carePlanId, Long credentialTypeId) {
	}
}
