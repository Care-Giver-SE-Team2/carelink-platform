package sg.nus.carelink.rostering.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sg.nus.carelink.coreapi.CoreApi;
import sg.nus.carelink.rostering.application.VisitCover;

/**
 * rostering's part of core's internal API ({@link CoreApi}): who can cover a visit, and handing it
 * to one of them.
 */
@RestController
@RequestMapping("/internal/v1")
public class InternalVisitCoverController {

	private final VisitCover visitCover;

	InternalVisitCoverController(VisitCover visitCover) {
		this.visitCover = visitCover;
	}

	@GetMapping("/visits/{visitId}/cover-options")
	public List<CoreApi.CoverOption> coverOptions(@PathVariable Long visitId) {
		return visitCover.options(visitId).stream()
				.map(option -> new CoreApi.CoverOption(option.caregiverId(), option.name(), option.rank(), option.reason()))
				.toList();
	}

	@PostMapping("/visits/{visitId}/cover")
	public ResponseEntity<Void> cover(@PathVariable Long visitId, @RequestBody CoreApi.CoverRequest request) {
		visitCover.cover(visitId, request.caregiverId(), request.byUserId());
		return ResponseEntity.noContent().build();
	}

}
