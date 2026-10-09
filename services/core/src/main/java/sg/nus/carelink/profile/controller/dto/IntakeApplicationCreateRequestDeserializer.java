package sg.nus.carelink.profile.controller.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

import sg.nus.carelink.profile.domain.model.IntakeApplication.MobilityLevel;

/**
 * Parses intake fields and rejects unknown fields, explicit nulls and invalid JSON types.
 *
 * @author Wang Zhili
 */
public class IntakeApplicationCreateRequestDeserializer extends ValueDeserializer<IntakeApplicationCreateRequest> {

	private static final Set<String> FIELDS = Set.of("targetElderName", "targetElderAge", "targetAddress",
			"postalCode", "mobilityLevel", "preferredDialects", "careNeeds", "medicalNotes");

	/**
	 * Read family-supplied application details from a JSON object.
	 *
	 * @param parser Parser positioned at the request body
	 * @param context JSON deserialization context for reading values and reporting invalid input
	 * @return Application details ready for field validation
	 *
	 * @author Wang Zhili
	 */
	@Override
	public IntakeApplicationCreateRequest deserialize(JsonParser parser, DeserializationContext context) {
		JsonNode input = context.readTree(parser);
		if (!input.isObject()) {
			return invalid(context, "Expected an application object");
		}
		for (var property : input.properties()) {
			if (!FIELDS.contains(property.getKey()) || property.getValue().isNull()) {
				return invalid(context, "Unexpected field or explicit null value");
			}
		}
		return new IntakeApplicationCreateRequest(text(input, "targetElderName", context),
				age(input, context), text(input, "targetAddress", context), text(input, "postalCode", context),
				mobility(input, context), text(input, "preferredDialects", context),
				careNeeds(input, context), text(input, "medicalNotes", context));
	}

	private static String text(JsonNode input, String field, DeserializationContext context) {
		JsonNode value = input.get(field);
		if (value == null) {
			return null;
		}
		return value.isString() ? value.stringValue() : invalid(context, field + " must be a string");
	}

	private static Integer age(JsonNode input, DeserializationContext context) {
		JsonNode value = input.get("targetElderAge");
		if (value == null) {
			return null;
		}
		return value.isIntegralNumber() && value.canConvertToInt()
				? value.intValue() : invalid(context, "targetElderAge must be a supported integer");
	}

	private static MobilityLevel mobility(JsonNode input, DeserializationContext context) {
		String value = text(input, "mobilityLevel", context);
		if (value == null) {
			return null;
		}
		try {
			return MobilityLevel.valueOf(value);
		} catch (IllegalArgumentException _) {
			return invalid(context, "Unsupported mobilityLevel");
		}
	}

	private static List<String> careNeeds(JsonNode input, DeserializationContext context) {
		JsonNode value = input.get("careNeeds");
		if (value == null) {
			return List.of();
		}
		if (!value.isArray()) {
			return invalid(context, "careNeeds must be an array");
		}
		var needs = new ArrayList<String>();
		for (JsonNode item : value) {
			if (!item.isString()) {
				return invalid(context, "careNeeds items must be strings");
			}
			needs.add(item.stringValue());
		}
		return needs;
	}

	private static <T> T invalid(DeserializationContext context, String message) {
		return context.reportInputMismatch(IntakeApplicationCreateRequest.class, message);
	}
}
