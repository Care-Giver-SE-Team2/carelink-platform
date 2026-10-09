package sg.nus.carelink.shared.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** SPA document routes reachable before signing in, so a reload or a shared link still opens them. */
@Controller
class PublicPageController {
    @GetMapping("/apply")
    String page() {
        return "forward:/index.html";
    }
}
