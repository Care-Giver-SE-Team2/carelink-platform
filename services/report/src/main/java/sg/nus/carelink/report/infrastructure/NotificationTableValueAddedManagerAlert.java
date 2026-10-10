package sg.nus.carelink.report.infrastructure;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import sg.nus.carelink.report.application.ValueAddedManagerAlert;

/**
 * Inserts in-app manager-bell notifications in the same transaction as approval. The bell links
 * a VISIT notification to the manager's Extra services screen, where a visit nobody holds is staffed.
 */
@Component
public class NotificationTableValueAddedManagerAlert implements ValueAddedManagerAlert {
    /** English month names whatever the server's locale. */
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.ENGLISH);

    private final JdbcClient jdbc;

    public NotificationTableValueAddedManagerAlert(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void approved(Long visitId, Long elderId, String serviceName, LocalDateTime start, Long caregiverId) {
        List<Long> managers = jdbc.sql("""
                select distinct u.id from app_user u
                join user_role r on r.user_id = u.id
                where r.role = 'MANAGER' and u.enabled = true
                """).query(Long.class).list();
        String title = caregiverId == null ? "Extra service needs caregiver assignment"
                : "Extra service approved and assigned";
        String body = name("select full_name from elder where id = :id", elderId, "Elder #") + ": " + serviceName + " on " + start.format(WHEN)
                + (caregiverId == null
                        ? ". The primary caregiver is not free; assign a caregiver in Extra services."
                        : ". " + name("select full_name from caregiver where id = :id", caregiverId, "Caregiver #") + " (primary caregiver) is on it. Review in Extra services.");
        for (Long managerId : managers) {
            jdbc.sql("""
                    insert into notification
                    (recipient_user_id,event_type,channel,title,body,resource_type,resource_id,status,created_at)
                    values (:recipient,'VALUE_ADDED_APPROVED','IN_APP',:title,:body,'VISIT',:visit,'PENDING',CURRENT_TIMESTAMP)
                    """).param("recipient", managerId).param("title", title)
                    .param("body", body.length() > 1000 ? body.substring(0, 1000) : body)
                    .param("visit", visitId).update();
        }
    }

    /** A person's full name for the message; "Elder #3" if the row has gone. */
    private String name(String sql, Long id, String fallbackPrefix) {
        return jdbc.sql(sql).param("id", id).query(String.class).optional().orElse(fallbackPrefix + id);
    }
}
