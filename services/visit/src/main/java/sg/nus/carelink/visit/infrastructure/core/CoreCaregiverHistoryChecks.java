package sg.nus.carelink.visit.infrastructure.core;
import java.util.List;
import org.springframework.stereotype.Component;
import sg.nus.carelink.coreapi.CoreApi;
import sg.nus.carelink.visit.application.CaregiverHistoryChecks;
@Component
class CoreCaregiverHistoryChecks implements CaregiverHistoryChecks {
 private final CoreApi core;
 CoreCaregiverHistoryChecks(CoreApi core){this.core=core;}
 public List<Check> forCaregiver(String username){return core.ownSpotChecks(username).stream().map(c->new Check(c.id(),c.visitId(),c.result(),c.checkedAt(),c.caregiverResponse())).toList();}
}
