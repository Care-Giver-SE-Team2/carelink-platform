package sg.nus.carelink.careplan.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import sg.nus.carelink.careplan.controller.dto.CareActivityResponse;

/**
 * The care activity catalog, read by the family application forms and the manager's plan editor
 * alike. Any signed-in role may read it; RequestContextInterceptor already turns away anonymous
 * calls to /api/**, so there is no @PreAuthorize here.
 */
@RestController
@RequestMapping("/api/care-activities")
public class CareActivityController {

	@GetMapping
	public List<CareActivityResponse> list() {
		return CareActivityResponse.catalog();
	}
}
