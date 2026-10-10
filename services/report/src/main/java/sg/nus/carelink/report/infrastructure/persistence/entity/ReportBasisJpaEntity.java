package sg.nus.carelink.report.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * JPA entity for table report_basis (V19): what one generation run read for one elder and one
 * period, and the numbers worked out from it.
 *
 * <p>Inserted once and never updated, so every column is {@code updatable = false}. The facts
 * are a String, as report.content is: Hibernate passes it to the JSON column untouched, and
 * turning the facts into that text is ReportBasisJson's job in the adapter.
 *
 * <p>The schema is owned by Flyway. Hibernate validates this mapping at start-up and never
 * alters the table.
 */
@Entity
@Table(name = "report_basis")
public class ReportBasisJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** soft FK to elder.id; the reports filed from this basis carry the hard one */
	@Column(name = "elder_id", nullable = false, updatable = false)
	private Long elderId;

	@Column(name = "period_start", nullable = false, updatable = false)
	private LocalDate periodStart;

	@Column(name = "period_end", nullable = false, updatable = false)
	private LocalDate periodEnd;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "facts", nullable = false, updatable = false)
	private String facts;

	@Column(name = "facts_version", nullable = false, updatable = false)
	private Short factsVersion;

	@Column(name = "visits_planned", nullable = false, updatable = false)
	private int visitsPlanned;

	@Column(name = "visits_completed", nullable = false, updatable = false)
	private int visitsCompleted;

	@Column(name = "fulfilment_rate", precision = 5, scale = 2, updatable = false)
	private BigDecimal fulfilmentRate;

	@Column(name = "vitals_out_of_range", nullable = false, updatable = false)
	private int vitalsOutOfRange;

	@Column(name = "incident_count", nullable = false, updatable = false)
	private int incidentCount;

	@Column(name = "avg_elder_rating", precision = 3, scale = 2, updatable = false)
	private BigDecimal avgElderRating;

	@Column(name = "rating_count", nullable = false, updatable = false)
	private int ratingCount;

	@Column(name = "data_complete", nullable = false, updatable = false)
	private boolean dataComplete;

	/** Written by the application, on the clock the run's reports are dated with; see ReportJpaEntity. */
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	public ReportBasisJpaEntity() {
		// JPA instantiates an entity through its no-argument constructor and then sets the
		// columns itself; the mapper does the same through the setters. Nothing to do here.
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getElderId() {
		return elderId;
	}

	public void setElderId(Long elderId) {
		this.elderId = elderId;
	}

	public LocalDate getPeriodStart() {
		return periodStart;
	}

	public void setPeriodStart(LocalDate periodStart) {
		this.periodStart = periodStart;
	}

	public LocalDate getPeriodEnd() {
		return periodEnd;
	}

	public void setPeriodEnd(LocalDate periodEnd) {
		this.periodEnd = periodEnd;
	}

	public String getFacts() {
		return facts;
	}

	public void setFacts(String facts) {
		this.facts = facts;
	}

	public Short getFactsVersion() {
		return factsVersion;
	}

	public void setFactsVersion(Short factsVersion) {
		this.factsVersion = factsVersion;
	}

	public int getVisitsPlanned() {
		return visitsPlanned;
	}

	public void setVisitsPlanned(int visitsPlanned) {
		this.visitsPlanned = visitsPlanned;
	}

	public int getVisitsCompleted() {
		return visitsCompleted;
	}

	public void setVisitsCompleted(int visitsCompleted) {
		this.visitsCompleted = visitsCompleted;
	}

	public BigDecimal getFulfilmentRate() {
		return fulfilmentRate;
	}

	public void setFulfilmentRate(BigDecimal fulfilmentRate) {
		this.fulfilmentRate = fulfilmentRate;
	}

	public int getVitalsOutOfRange() {
		return vitalsOutOfRange;
	}

	public void setVitalsOutOfRange(int vitalsOutOfRange) {
		this.vitalsOutOfRange = vitalsOutOfRange;
	}

	public int getIncidentCount() {
		return incidentCount;
	}

	public void setIncidentCount(int incidentCount) {
		this.incidentCount = incidentCount;
	}

	public BigDecimal getAvgElderRating() {
		return avgElderRating;
	}

	public void setAvgElderRating(BigDecimal avgElderRating) {
		this.avgElderRating = avgElderRating;
	}

	public int getRatingCount() {
		return ratingCount;
	}

	public void setRatingCount(int ratingCount) {
		this.ratingCount = ratingCount;
	}

	public boolean isDataComplete() {
		return dataComplete;
	}

	public void setDataComplete(boolean dataComplete) {
		this.dataComplete = dataComplete;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}
}
