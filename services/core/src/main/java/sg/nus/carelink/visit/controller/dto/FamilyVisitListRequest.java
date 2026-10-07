package sg.nus.carelink.visit.controller.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.format.annotation.DateTimeFormat;

import sg.nus.carelink.visit.domain.model.Visit;
import sg.nus.carelink.visit.domain.model.VisitScheduleFilter;

/**
 * Validates family schedule query parameters.
 *
 * @author Wang Zhili
 */
public class FamilyVisitListRequest {

	@Min(1)
	private Long elderId;
	@Min(1)
	private Long caregiverId;
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate dateFrom;
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate dateTo;
	@Pattern(regexp = "SCHEDULED|ARRIVED|IN_PROGRESS|COMPLETED|VERIFIED|AUTO_CLOSED|EXCEPTION|CANCELLED")
	private String status;
	@Min(0)
	private int page = 0;
	@Min(1)
	@Max(200)
	private int size = 20;

	@AssertTrue(message = "dateFrom and dateTo must be provided together, in order, within 1000-01-01 and 9999-12-30")
	public boolean isDateRangeValid() {
		return VisitScheduleFilter.validDates(dateFrom, dateTo);
	}

	public VisitScheduleFilter toFilter() {
		return new VisitScheduleFilter(elderId, caregiverId, dateFrom, dateTo,
				status == null ? null : Visit.Status.valueOf(status), page, size);
	}

	public Long getElderId() { return elderId; }
	public void setElderId(Long elderId) { this.elderId = elderId; }
	public Long getCaregiverId() { return caregiverId; }
	public void setCaregiverId(Long caregiverId) { this.caregiverId = caregiverId; }
	public LocalDate getDateFrom() { return dateFrom; }
	public void setDateFrom(LocalDate dateFrom) { this.dateFrom = dateFrom; }
	public LocalDate getDateTo() { return dateTo; }
	public void setDateTo(LocalDate dateTo) { this.dateTo = dateTo; }
	public String getStatus() { return status; }
	public void setStatus(String status) { this.status = status; }
	public int getPage() { return page; }
	public void setPage(int page) { this.page = page; }
	public int getSize() { return size; }
	public void setSize(int size) { this.size = size; }
}
