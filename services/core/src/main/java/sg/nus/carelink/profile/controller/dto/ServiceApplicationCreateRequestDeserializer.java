package sg.nus.carelink.profile.controller.dto;

import java.util.ArrayList;
import java.util.Set;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

/** Reject forged owner/snapshot/status fields and lossy numeric or string coercions at the boundary. */
public class ServiceApplicationCreateRequestDeserializer extends ValueDeserializer<ServiceApplicationCreateRequest> {
    private static final Set<String> FIELDS = Set.of("elderId", "careNeeds", "notes");

    @Override
    public ServiceApplicationCreateRequest deserialize(JsonParser parser, DeserializationContext context) {
        JsonNode input = context.readTree(parser);
        if (!input.isObject() || input.propertyNames().stream().anyMatch(name -> !FIELDS.contains(name))) {
            return invalid(context);
        }
        JsonNode id = input.get("elderId");
        JsonNode needs = input.get("careNeeds");
        JsonNode notes = input.get("notes");
        if (id == null || !id.isIntegralNumber() || !id.canConvertToLong() || needs == null || !needs.isArray()
                || (notes != null && !notes.isNull() && !notes.isString())) {
            return invalid(context);
        }
        var services = new ArrayList<String>();
        for (JsonNode need : needs) {
            if (!need.isString()) return invalid(context);
            services.add(need.stringValue().strip());
        }
        return new ServiceApplicationCreateRequest(id.longValue(), services,
                notes == null || notes.isNull() ? null : notes.stringValue().strip());
    }

    private static <T> T invalid(DeserializationContext context) {
        return context.reportInputMismatch(ServiceApplicationCreateRequest.class,
                "Expected elderId, careNeeds and optional notes only, with their declared JSON types");
    }
}
