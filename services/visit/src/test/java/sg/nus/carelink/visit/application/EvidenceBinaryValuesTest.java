package sg.nus.carelink.visit.application;

import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import sg.nus.carelink.visit.domain.repository.EvidenceImageStorage.Prepared;
import static org.assertj.core.api.Assertions.assertThat;

class EvidenceBinaryValuesTest {
    @Test void preparedImagesCompareContentAndRemainStableAfterExternalMutation() {
        byte[] source={1,2,3};
        var image=new Prepared("demo.png","image/png",source,"original","stored");
        var same=new Prepared("demo.png","image/png",new byte[]{1,2,3},"original","stored");
        var values=new HashSet<>(List.of(image));
        source[0]=9;
        image.bytes()[1]=9;
        assertThat(image.bytes()).containsExactly((byte)1,(byte)2,(byte)3);
        assertThat(image).isEqualTo(same);
        assertThat(image.hashCode()).isEqualTo(same.hashCode());
        assertThat(values).contains(same);
        assertThat(image.toString()).isEqualTo("Prepared evidence image");
    }

    @Test void preparedImagesDistinguishMetadataAndBytesWithoutMatchingOtherTypes() {
        var image=new Prepared("demo.png","image/png",new byte[]{1},"original","stored");
        for(var different:List.of(
                new Prepared("other.png","image/png",new byte[]{1},"original","stored"),
                new Prepared("demo.png","image/jpeg",new byte[]{1},"original","stored"),
                new Prepared("demo.png","image/png",new byte[]{2},"original","stored"),
                new Prepared("demo.png","image/png",new byte[]{1},"other","stored"),
                new Prepared("demo.png","image/png",new byte[]{1},"original","other")))
            assertThat(image).isNotEqualTo(different);
        assertThat(image.equals(null)).isFalse();
        assertThat(image.equals("unrelated")).isFalse();
    }

    @Test void downloadedContentProtectsBothInputAndReturnedArrays() {
        byte[] source={4,5};
        var content=new CaregiverEvidenceService.Content(null,source);
        var same=new CaregiverEvidenceService.Content(null,new byte[]{4,5});
        var values=new HashSet<>(List.of(content));
        source[0]=9;
        content.bytes()[1]=9;
        assertThat(content.bytes()).containsExactly((byte)4,(byte)5);
        assertThat(content).isEqualTo(same);
        assertThat(content.hashCode()).isEqualTo(same.hashCode());
        assertThat(values).contains(same);
        assertThat(content.toString()).isEqualTo("Private evidence content");
    }

    @Test void downloadedContentDistinguishesBytesAndOtherTypes() {
        var content=new CaregiverEvidenceService.Content(null,new byte[]{1});
        assertThat(content).isNotEqualTo(new CaregiverEvidenceService.Content(null,new byte[]{2}));
        assertThat(content.equals(null)).isFalse();
        assertThat(content.equals("unrelated")).isFalse();
    }
}
