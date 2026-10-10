package sg.nus.carelink.notification.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * JPA entity for table notification_subscription.
 *
 * NOT in any submission. UC-FM03 (live feed) and UC-FM05 (know about an
 * incident at once) both need a record of what was sent to whom. Proof of
 * concept sends IN_APP only; the channel column exists so SMS or email can
 * be added as another Observer without touching the tables.
 *
 * DECISION 18  A notification is one row per recipient, created when a
 *              domain event happens (visit state change, incident raised or
 *              escalated, credential expiring, spot check requested, roster
 *              changed). What a family member receives is decided by their
 *              subscription AND by elder_family_binding.access_scope: the
 *              subscription says what they want, the binding says what they
 *              may see.
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
@Table(name = "notification_subscription")
public class NotificationSubscriptionJpaEntity {

	public enum Channel {
		IN_APP, SMS, EMAIL
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** soft FK to app_user.id */
	@Column(name = "user_id", nullable = false)
	private Long userId;

	/** whose events; null = events about the user themself, e.g. credential expiring */
	@Column(name = "elder_id")
	private Long elderId;

	/** VISIT_STARTED, VISIT_COMPLETED, INCIDENT_RAISED, INCIDENT_ESCALATED, CREDENTIAL_EXPIRING, SPOT_CHECK_REQUESTED, ROSTER_CHANGED */
	@Column(name = "event_type", nullable = false, length = 40)
	private String eventType;

	@Enumerated(EnumType.STRING)
	@Column(name = "channel", nullable = false)
	private Channel channel = Channel.IN_APP;

	@Column(name = "enabled", nullable = false)
	private boolean enabled = true;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime createdAt;

	public NotificationSubscriptionJpaEntity() {
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getUserId() {
		return userId;
	}

	public void setUserId(Long userId) {
		this.userId = userId;
	}

	public Long getElderId() {
		return elderId;
	}

	public void setElderId(Long elderId) {
		this.elderId = elderId;
	}

	public String getEventType() {
		return eventType;
	}

	public void setEventType(String eventType) {
		this.eventType = eventType;
	}

	public Channel getChannel() {
		return channel;
	}

	public void setChannel(Channel channel) {
		this.channel = channel;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
