package sg.nus.carelink.careplan.infrastructure.persistence.entity;

import java.time.LocalDateTime;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for table care_plan_node_visit: one day of a task's schedule. Owned by
 * {@link CarePlanNodeJpaEntity#getVisits()}, which writes care_plan_node_id; never saved on its own.
 */
@Entity
@Table(name = "care_plan_node_visit")
public class CarePlanNodeVisitJpaEntity {

	public enum Day {
		MON, TUE, WED, THU, FRI, SAT, SUN
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(name = "day_of_week", nullable = false)
	private Day dayOfWeek;

	@Column(name = "start_time", nullable = false)
	private LocalTime startTime;

	@Column(name = "minutes", nullable = false)
	private Integer minutes;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime createdAt;

	public CarePlanNodeVisitJpaEntity() {
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Day getDayOfWeek() {
		return dayOfWeek;
	}

	public void setDayOfWeek(Day dayOfWeek) {
		this.dayOfWeek = dayOfWeek;
	}

	public LocalTime getStartTime() {
		return startTime;
	}

	public void setStartTime(LocalTime startTime) {
		this.startTime = startTime;
	}

	public Integer getMinutes() {
		return minutes;
	}

	public void setMinutes(Integer minutes) {
		this.minutes = minutes;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
