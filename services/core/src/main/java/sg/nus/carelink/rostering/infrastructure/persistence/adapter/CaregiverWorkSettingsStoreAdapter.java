package sg.nus.carelink.rostering.infrastructure.persistence.adapter;

import java.sql.Timestamp;
import java.sql.Date;
import java.sql.Time;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;
import sg.nus.carelink.rostering.domain.model.*;
import sg.nus.carelink.rostering.domain.model.CaregiverWorkSettings.*;
import sg.nus.carelink.rostering.domain.repository.CaregiverWorkSettingsStore;
import sg.nus.carelink.rostering.domain.service.WorkSettingsRules;
import sg.nus.carelink.shared.error.ResourceNotFound;

@Repository
class CaregiverWorkSettingsStoreAdapter implements CaregiverWorkSettingsStore {
    private static final JsonMapper JSON=JsonMapper.builder().build();
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate batch;
    CaregiverWorkSettingsStoreAdapter(JdbcTemplate jdbc) { this.jdbc=jdbc; this.batch=new NamedParameterJdbcTemplate(jdbc); }
    @Override public String lock(Long id) {
        String status=jdbc.query("select status from caregiver where id=? for update",(rs,n)->rs.getString(1),id)
                .stream().findFirst().orElseThrow(()->new ResourceNotFound("Caregiver",id));
        jdbc.update("insert into caregiver_work_settings_state(caregiver_id) values (?) on duplicate key update caregiver_id=caregiver_id",id);
        return status;
    }
    @Override public Map<Long,State> states(Set<Long> ids) {
        var result=new LinkedHashMap<Long,State>();
        if(ids.isEmpty()) return result;
        batch.query("select c.id,coalesce(s.version,0) as version,s.updated_at from caregiver c left join caregiver_work_settings_state s on s.caregiver_id=c.id where c.id in (:ids) order by c.id",Map.of("ids",ids),rs->{
            long id=rs.getLong("id"); var time=rs.getTimestamp("updated_at");
            result.put(id,new State(id,rs.getLong("version"),time==null ? null : time.toLocalDateTime()));
        });
        return result;
    }
    @Override public Map<Long,CaregiverPreference> preferences(Set<Long> ids) {
        var result=new HashMap<Long,CaregiverPreference>();
        if(ids.isEmpty()) return result;
        batch.query("select * from caregiver_preference where caregiver_id in (:ids)",Map.of("ids",ids),rs->{
            long id=rs.getLong("caregiver_id");
            result.put(id,new CaregiverPreference(rs.getLong("id"),id,rs.getString("preferred_service_types"),rs.getString("preferred_sectors"),
                    rs.getString("preferred_time_windows"),rs.getObject("max_visits_per_day",Integer.class),rs.getBigDecimal("max_hours_per_day"),rs.getTimestamp("updated_at").toLocalDateTime()));
        });
        return result;
    }
    @Override public List<CaregiverAvailability> availability(Set<Long> ids,LocalDate from,LocalDate to) {
        if(ids.isEmpty()) return List.of();
        return batch.query("select * from caregiver_availability where caregiver_id in (:ids) and available_date between :from and :to order by caregiver_id,available_date,available_start,id",
                Map.of("ids",ids,"from",Date.valueOf(from),"to",Date.valueOf(to)),(rs,n)->new CaregiverAvailability(rs.getLong("id"),rs.getLong("caregiver_id"),
                        rs.getDate("available_date").toLocalDate(),rs.getTime("available_start").toLocalTime(),rs.getTime("available_end").toLocalTime(),
                        CaregiverAvailability.Status.valueOf(rs.getString("status")),rs.getTimestamp("created_at").toLocalDateTime()));
    }
    @Override public void replaceDay(Long id,LocalDate date,List<Window> windows) {
        jdbc.update("delete from caregiver_availability where caregiver_id=? and available_date=?",id,Date.valueOf(date));
        for(var w:windows) jdbc.update("insert into caregiver_availability(caregiver_id,available_date,available_start,available_end,status) values (?,?,?,?,?)",id,Date.valueOf(date),Time.valueOf(w.start()),Time.valueOf(w.end()),w.status().name());
    }
    @Override public void preferences(Long id,Preferences p,LocalDateTime now) {
        jdbc.update("insert into caregiver_preference(caregiver_id,preferred_service_types,preferred_sectors,preferred_time_windows,max_visits_per_day,max_hours_per_day,updated_at) values (?,?,?,?,?,?,?) on duplicate key update preferred_service_types=?,preferred_sectors=?,preferred_time_windows=?,max_visits_per_day=?,max_hours_per_day=?,updated_at=?",
                id,WorkSettingsRules.csv(p.serviceCodes()),WorkSettingsRules.csv(p.sectors()),WorkSettingsRules.weeklyCsv(p.weeklyWindows()),p.maxVisitsPerDay(),p.maxHoursPerDay(),Timestamp.valueOf(now),
                WorkSettingsRules.csv(p.serviceCodes()),WorkSettingsRules.csv(p.sectors()),WorkSettingsRules.weeklyCsv(p.weeklyWindows()),p.maxVisitsPerDay(),p.maxHoursPerDay(),Timestamp.valueOf(now));
    }
    @Override public void advance(Long id,LocalDateTime now) { jdbc.update("update caregiver_work_settings_state set version=version+1,updated_at=? where caregiver_id=?",Timestamp.valueOf(now),id); }
    @Override public Optional<Receipt> receipt(Long actor,String key) {
        return jdbc.query("select payload_sha256,result_snapshot from caregiver_work_settings_receipt where actor_user_id=? and client_request_id=?",
                (rs,n)->new Receipt(rs.getString(1),JSON.readValue(rs.getString(2),Snapshot.class)),actor,key).stream().findFirst();
    }
    @Override public void receipt(Long actor,String key,String hash,Snapshot result) {
        jdbc.update("insert into caregiver_work_settings_receipt(actor_user_id,client_request_id,caregiver_id,payload_sha256,result_snapshot) values (?,?,?,?,?)",actor,key,result.caregiverId(),hash,JSON.writeValueAsString(result));
    }
    @Override public void audit(Long actor,Long caregiver,String action,LocalDateTime now) {
        jdbc.update("insert into audit_log(actor_user_id,action,resource_type,resource_id,result,occurred_at) values (?,?,'CAREGIVER_WORK_SETTINGS',?,'OK',?)",actor,action,caregiver,Timestamp.valueOf(now));
    }
}
