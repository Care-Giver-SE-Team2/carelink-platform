package sg.nus.carelink.shared.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** Only SPA document routes; API and asset failures must retain their real HTTP status. */
@Controller
class CaregiverPageController {
    @GetMapping({"/caregiver", "/caregiver/", "/caregiver/visits/{visitId}", "/caregiver/visits/{visitId}/report-incident",
            "/caregiver/absences", "/caregiver/spot-checks", "/caregiver/incidents", "/caregiver/incidents/{incidentId}"})
    String page(@PathVariable(name = "visitId", required = false) String visitId,
            @PathVariable(name = "incidentId", required = false) String incidentId) {
        return "forward:/index.html";
    }
}
