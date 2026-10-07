package sg.nus.carelink.shared.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** Only SPA document routes; API and asset failures must retain their real HTTP status. */
@Controller
class CaregiverPageController {
    @GetMapping({"/caregiver", "/caregiver/", "/caregiver/visits/{visitId}"})
    String page(@PathVariable(name = "visitId", required = false) String visitId) {
        return "forward:/index.html";
    }
}
