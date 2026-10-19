package sg.nus.carelink.testsupport;

import java.util.HashSet;
import java.util.Set;

/**
 * Which tables of the shared schema belong to which service (docs/platform/service-boundaries.md,
 * sections 1 and 6). Every other table core's migrations build is core's.
 */
public final class ServiceTables {

	public static final Set<String> VISIT = Set.of("caregiver_command_receipt", "elder_confirmation", "visit",
			"visit_assignment", "visit_check_in_record", "visit_evidence", "visit_health_record",
			"visit_missed_check_in_trigger", "visit_request", "visit_state_transition", "visit_task", "vital_sign");

	public static final Set<String> REPORT = Set.of("caregiver_review", "report", "report_amendment", "report_basis",
			"value_added_service", "value_added_service_request", "report_visit", "report_visit_task",
			"report_vital_sign", "report_elder_confirmation", "report_incident", "report_incident_log",
			"report_spot_check", "report_roster_change", "report_provider", "report_provider_service",
			"report_provider_job", "report_provider_job_key");

	public static final Set<String> NOTIFICATION = Set.of("notification", "notification_subscription",
			"provider_webhook_endpoint", "webhook_delivery", "webhook_delivery_attempt", "provider_application",
			"provider", "provider_portal_user", "provider_client", "provider_token_key");

	/** What every service keeps in its own database: the access audit, the scheduler locks, the events tables. */
	public static final Set<String> EACH_SERVICE = Set.of("audit_log", "shedlock", "outbox_event", "consumed_message");

	public static final Set<String> ALL = union(VISIT, REPORT, NOTIFICATION, EACH_SERVICE);

	/** core's own tables, which stay in core's database. */
	public static final Set<String> CORE = Set.of("care_plan", "care_plan_node", "care_plan_node_visit",
			"care_plan_required_credential", "app_user", "user_role", "family_alert_delivery", "family_alert_event",
			"family_alert_window", "incident", "incident_acknowledgement", "incident_log", "incident_request",
			"spot_check", "care_service_application", "caregiver", "credential", "credential_type", "elder",
			"elder_family_binding", "elder_primary_caregiver", "family_member", "intake_application", "absence_report",
			"caregiver_availability", "caregiver_preference", "roster_change", "rostering_candidate",
			"rostering_candidate_check", "rostering_constraint", "rostering_run", "visit_change_step");

	/** Every table a service's code could name. */
	public static final Set<String> KNOWN = union(ALL, CORE);

	private ServiceTables() {
	}

	@SafeVarargs
	private static Set<String> union(Set<String>... sets) {
		Set<String> all = new HashSet<>();
		for (Set<String> set : sets) {
			all.addAll(set);
		}
		return Set.copyOf(all);
	}

}
