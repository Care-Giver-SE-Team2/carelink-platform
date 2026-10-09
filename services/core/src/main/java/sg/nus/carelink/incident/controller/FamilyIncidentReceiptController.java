package sg.nus.carelink.incident.controller;

import java.security.Principal;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

import sg.nus.carelink.incident.application.FamilyIncidentReceiptService;
import sg.nus.carelink.incident.controller.dto.FamilyIncidentAcknowledgeRequest;
import sg.nus.carelink.incident.controller.dto.FamilyIncidentDetailResponse.Acknowledgement;

/** Family-only explicit display and acknowledgement commands. @author Wang Zhili */
@RestController
@RequestMapping("/api/incidents")
public class FamilyIncidentReceiptController {

	private final FamilyIncidentReceiptService receipts;

	public FamilyIncidentReceiptController(FamilyIncidentReceiptService receipts) { this.receipts = receipts; }

	@PostMapping("/{id}/view")
	@PreAuthorize("hasRole('FAMILY')")
	public Acknowledgement view(@PathVariable Long id, @RequestBody(required = false) JsonNode body, Principal principal) {
		if (body != null) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This operation accepts no request body"); }
		return Acknowledgement.of(receipts.view(principal.getName(), id));
	}

	@PostMapping("/{id}/acknowledge")
	@PreAuthorize("hasRole('FAMILY')")
	public Acknowledgement acknowledge(@PathVariable Long id,
			@Valid @RequestBody(required = false) FamilyIncidentAcknowledgeRequest body, Principal principal) {
		return Acknowledgement.of(receipts.acknowledge(principal.getName(), id, body == null ? null : body.responseNote()));
	}
}
