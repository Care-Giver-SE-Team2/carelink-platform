package sg.nus.carelink.report.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PostalDistrictsTest {

    @Test
    void theDistrictComesFromThePostalCodeAtTheEndOfTheAddress() {
        assertThat(PostalDistricts.of("Blk 123 Hougang Ave 1, #05-67, Singapore 530123"))
                .hasValue(new PostalDistricts.District("19", "Hougang, Punggol, Serangoon Gardens"));
        assertThat(PostalDistricts.of("Blk 88 Toa Payoh Lor 4, #11-09 310088"))
                .hasValueSatisfying(district -> assertThat(district.number()).isEqualTo("12"));
        assertThat(PostalDistricts.of("018989")).hasValueSatisfying(d -> assertThat(d.number()).isEqualTo("01"));
    }

    @Test
    void anAddressWithoutAKnownPostalCodeHasNoDistrict() {
        assertThat(PostalDistricts.of("30 Fictional Demo Road")).isEmpty();
        assertThat(PostalDistricts.of("Unit 1234567")).isEmpty();
        assertThat(PostalDistricts.of("990000")).isEmpty();
        assertThat(PostalDistricts.of(null)).isEmpty();
    }
}
