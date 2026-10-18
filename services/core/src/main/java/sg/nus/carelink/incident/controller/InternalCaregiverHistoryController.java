package sg.nus.carelink.incident.controller;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import sg.nus.carelink.coreapi.CoreApi;
import sg.nus.carelink.incident.application.SpotCheckService;
@RestController
@RequestMapping("/internal/v1")
public class InternalCaregiverHistoryController {
 private final SpotCheckService checks;
 public InternalCaregiverHistoryController(SpotCheckService checks){this.checks=checks;}
 @GetMapping("/caregivers/by-username/{username}/own-spot-checks")
 public List<CoreApi.OwnSpotCheck> own(@PathVariable String username){return checks.forCaregiver(username).stream().map(c->new CoreApi.OwnSpotCheck(c.id(),c.visitId(),c.result()==null?null:c.result().name(),c.checkedAt(),c.caregiverResponse())).toList();}
}
