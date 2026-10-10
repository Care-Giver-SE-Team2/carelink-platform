package sg.nus.carelink.visit.application;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import sg.nus.carelink.profile.application.CaregiverDirectory;
import sg.nus.carelink.profile.application.CaregiverPublicProfile;
import sg.nus.carelink.profile.application.CaregiverPublicCredential;
import sg.nus.carelink.profile.application.FamilyAccessQuery;
import sg.nus.carelink.profile.application.FamilyReadAudit;
import sg.nus.carelink.shared.error.ResourceNotFound;
import sg.nus.carelink.visit.domain.repository.VisitScheduleQuery;

/**
 * Reads caregiver profiles and credentials after current family and visit relationship checks.
 *
 * @author Wang Zhili
 */
@Service
public class FamilyCaregiverQueryService {

	private final FamilyAccessQuery access;
	private final VisitScheduleQuery visits;
	private final CaregiverDirectory caregivers;
	private final FamilyReadAudit audit;

	public FamilyCaregiverQueryService(FamilyAccessQuery access, VisitScheduleQuery visits,
			CaregiverDirectory caregivers, FamilyReadAudit audit) {
		this.access = access;
		this.visits = visits;
		this.caregivers = caregivers;
		this.audit = audit;
	}

	/**
	 * Returns public details of a caregiver assigned to a currently readable elder's visit.
	 *
	 * @param authenticatedUsername Account supplied by the authenticated session
	 * @param caregiverId Caregiver profile identifier from the request path
	 * @return Public caregiver details
	 * @throws AccessDeniedException If current family access or a visit relationship is absent
	 * @throws ResourceNotFound If the authorized caregiver profile is unavailable
	 * @author Wang Zhili
	 */
	public CaregiverPublicProfile getProfile(String authenticatedUsername, Long caregiverId) {
		return audit.read(authenticatedUsername, FamilyReadAudit.Resource.CAREGIVER, caregiverId, "", () -> {
			requireRelationship(authenticatedUsername, caregiverId);
			return caregivers.findPublicProfile(caregiverId)
					.orElseThrow(() -> new ResourceNotFound("Caregiver", caregiverId));
		});
	}

	/**
	 * Lists public credentials after independently checking current caregiver access.
	 *
	 * @param authenticatedUsername Account supplied by the authenticated session
	 * @param caregiverId Caregiver profile identifier from the request path
	 * @return Ordered public credentials, or an empty list
	 * @author Wang Zhili
	 */
	public List<CaregiverPublicCredential> listCredentials(String authenticatedUsername, Long caregiverId) {
		return audit.read(authenticatedUsername, FamilyReadAudit.Resource.CREDENTIALS, caregiverId, "", () -> {
			requireRelationship(authenticatedUsername, caregiverId);
			return caregivers.listPublicCredentials(caregiverId);
		});
	}

	private void requireRelationship(String authenticatedUsername, Long caregiverId) {
		var elderIds = access.readableElderIds(authenticatedUsername);
		if (elderIds.isEmpty() || !visits.hasAssignedVisit(elderIds, caregiverId)) {
			throw new AccessDeniedException("A caregiver relationship with a readable elder is required");
		}
	}
}
