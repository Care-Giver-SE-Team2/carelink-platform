package sg.nus.carelink.profile.application;

import java.time.LocalDate;
import java.util.List;

/**
 * Cross-module contract for UC-MG06: for each certificate in the manager's register, the last
 * day it covers its caregiver, so rostering can count the booked visits it puts at risk.
 * Rostering imports this interface only, never profile's domain or infrastructure.
 */
public interface CredentialRegister {

	/** One entry per register row, in register order, with the rows that cover nobody left out. */
	List<Cover> covers();

	/**
	 * {@code coveredUntil} is the last day the caregiver may work visits that need this type on
	 * the strength of this row: its expiry, the replaced certificate's expiry for a pending
	 * renewal, 9999-12-31 when it never expires, or yesterday when they do not hold it yet.
	 */
	record Cover(Long credentialId, Long caregiverId, Long credentialTypeId, LocalDate coveredUntil) {
	}
}
