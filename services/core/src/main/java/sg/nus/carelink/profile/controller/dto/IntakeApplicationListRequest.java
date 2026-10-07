package sg.nus.carelink.profile.controller.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

import sg.nus.carelink.profile.domain.model.IntakeApplication;

/**
 * Validates optional status and pagination parameters for the intake list.
 *
 * @author Wang Zhili
 */
public class IntakeApplicationListRequest {

	@Min(0)
	private int page = 0;

	@Min(1)
	@Max(200)
	private int size = 20;

	@Pattern(regexp = "SUBMITTED|UNDER_REVIEW|APPROVED|REJECTED",
			message = "must be SUBMITTED, UNDER_REVIEW, APPROVED or REJECTED")
	private String status;

	public int getPage() {
		return page;
	}

	public void setPage(int page) {
		this.page = page;
	}

	public int getSize() {
		return size;
	}

	public void setSize(int size) {
		this.size = size;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public IntakeApplication.Status toStatus() {
		return status == null ? null : IntakeApplication.Status.valueOf(status);
	}
}
