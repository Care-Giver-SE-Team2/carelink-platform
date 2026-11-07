package sg.nus.carelink.report.controller.open;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import sg.nus.carelink.report.application.ValueAddedServiceCatalogueService;

/**
 * {@code GET /api/open/v1/services}: the services a provider may offer, for the Partner Portal's application
 * form and its services page (docs/api/open-platform.yaml, Catalogue). Open, without a token: the names are
 * what elders and families see too. A manager adds to them from the Extra services screen (D21).
 */
@RestController
public class OpenServicesController {

	private final ValueAddedServiceCatalogueService catalogue;

	public OpenServicesController(ValueAddedServiceCatalogueService catalogue) {
		this.catalogue = catalogue;
	}

	@GetMapping("/api/open/v1/services")
	public Services list() {
		return new Services(catalogue.offerableNames());
	}

	/** As the portal's own services are shaped: {@code {"services": [...]}}. */
	public record Services(List<String> services) {
	}
}
