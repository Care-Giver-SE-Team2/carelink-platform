package sg.nus.carelink.incident.controller.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

/** Optional plain-text note; identity and timestamps are never request fields. @author Wang Zhili */
public record FamilyIncidentAcknowledgeRequest(@Size(max = 255) String responseNote) {

	@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
	public static FamilyIncidentAcknowledgeRequest from(JsonNode node) {
		if (!node.isObject() || node.size() > 1 || node.size() == 1 && !node.has("responseNote")) {
			throw new IllegalArgumentException("Only responseNote is accepted");
		}
		var note = node.get("responseNote");
		if (note != null && !note.isNull() && !note.isString()) {
			throw new IllegalArgumentException("responseNote must be a string or null");
		}
		return new FamilyIncidentAcknowledgeRequest(note == null || note.isNull() ? null : note.asString());
	}
}
