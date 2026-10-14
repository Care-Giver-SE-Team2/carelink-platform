package sg.nus.carelink.openplatform.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The signature partners verify. The known answer is the test vector published in
 * docs/api/webhooks.md, section 5, worked out independently with openssl and Python: if this test
 * changes, the spec's vector must change with it, and every provider's verification breaks.
 */
class WebhookSignatureTest {

	private static final String SECRET = "whsec_example_do_not_use";

	private static final long TIMESTAMP = 1791864000L; // 2026-10-13 12:00 Singapore time

	private static final String BODY =
			"{\"id\":\"dlv_0000test\",\"type\":\"ping\",\"createdAt\":\"2026-10-13T12:00:00+08:00\",\"data\":{}}";

	private static final String KNOWN_ANSWER = "929d8bf95a92a264f468f1d6f0be8e2b88f07be25e4610b319fdb3e9987be1f8";

	@Test
	void signsTheSpecsTestVectorToItsPublishedAnswer() {
		assertThat(WebhookSignature.sign(SECRET, TIMESTAMP, BODY)).isEqualTo(KNOWN_ANSWER);
		assertThat(WebhookSignature.header(List.of(SECRET), TIMESTAMP, BODY)).isEqualTo("v1=" + KNOWN_ANSWER);
	}

	@Test
	void theSignatureCoversTheTimestampAndEveryByteOfTheBody() {
		assertThat(WebhookSignature.sign(SECRET, TIMESTAMP + 1, BODY)).isNotEqualTo(KNOWN_ANSWER);
		assertThat(WebhookSignature.sign(SECRET, TIMESTAMP, BODY + " ")).isNotEqualTo(KNOWN_ANSWER);
		assertThat(WebhookSignature.sign(SECRET + "x", TIMESTAMP, BODY)).isNotEqualTo(KNOWN_ANSWER);
	}

	@Test
	void duringARotationTheHeaderCarriesBothSignaturesNewestFirst() {
		String header = WebhookSignature.header(List.of("whsec_new", SECRET), TIMESTAMP, BODY);

		assertThat(header).isEqualTo("v1=" + WebhookSignature.sign("whsec_new", TIMESTAMP, BODY) + ",v1=" + KNOWN_ANSWER);
	}

	@Test
	void signsTheBodyAsUtf8() {
		String body = "{\"note\":\"Mdm Tan – 陈\"}";

		assertThat(WebhookSignature.sign(SECRET, TIMESTAMP, body)).hasSize(64).matches("[0-9a-f]+");
		assertThat(WebhookSignature.sign(SECRET, TIMESTAMP, body))
				.isNotEqualTo(WebhookSignature.sign(SECRET, TIMESTAMP, "{\"note\":\"Mdm Tan - ?\"}"));
	}

	@Test
	void aWebhookIsSignedWithAtLeastOneSecret() {
		assertThatThrownBy(() -> WebhookSignature.header(List.of(), TIMESTAMP, BODY))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
