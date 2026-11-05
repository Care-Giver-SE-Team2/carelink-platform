package sg.nus.carelink.profile.domain.service;

import org.junit.jupiter.api.Test;
import sg.nus.carelink.profile.application.CaregiverCredentialService.Content;
import sg.nus.carelink.profile.domain.repository.CredentialDocumentStorage.Upload;
import sg.nus.carelink.profile.domain.repository.CredentialDocumentStorage.Prepared;
import static org.assertj.core.api.Assertions.*;

class CredentialBinaryValueTest {
    @Test void uploadsCompareByteContentAndProtectBothArrayBoundaries() {
        byte[] bytes={1,2,3}; var first=new Upload("demo.png","image/png",bytes);
        var same=new Upload("demo.png","image/png",new byte[]{1,2,3});
        bytes[0]=9; byte[] returned=first.bytes(); returned[0]=8;
        assertThat(first).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(first).isNotEqualTo(new Upload("other.png","image/png",new byte[]{1,2,3}));
        assertThat(first).isNotEqualTo(new Upload("demo.png","image/jpeg",new byte[]{1,2,3}));
        assertThat(first).isNotEqualTo(new Upload("demo.png","image/png",new byte[]{3,2,1}));
        assertThat(first.equals(null)).isFalse();
        assertThat(first.toString()).contains("bytesHash=").doesNotContain("[1, 2, 3]");
        var empty=new Upload(null,null,null);
        assertThat(empty.bytes()).isNull(); assertThat(empty).isEqualTo(new Upload(null,null,null));
    }
    @Test void preparedValuesIncludeOriginalHashWithoutExposingOrMutatingBytes() {
        byte[] bytes={1,2}; var first=new Prepared("demo.pdf","application/pdf",bytes,"original");
        var same=new Prepared("demo.pdf","application/pdf",new byte[]{1,2},"original");
        bytes[0]=8; first.bytes()[0]=9;
        assertThat(first).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(first).isNotEqualTo(new Prepared("other.pdf","application/pdf",new byte[]{1,2},"original"));
        assertThat(first).isNotEqualTo(new Prepared("demo.pdf","image/png",new byte[]{1,2},"original"));
        assertThat(first).isNotEqualTo(new Prepared("demo.pdf","application/pdf",new byte[]{2,1},"original"));
        assertThat(first).isNotEqualTo(new Prepared("demo.pdf","application/pdf",new byte[]{1,2},"different"));
        assertThat(first.equals(null)).isFalse();
        assertThat(first.toString()).contains("bytesHash=").doesNotContain("[1, 2]");
        var empty=new Prepared(null,null,null,null);
        assertThat(empty.bytes()).isNull(); assertThat(empty).isEqualTo(new Prepared(null,null,null,null));
    }
    @Test void downloadContentComparesAndDefensivelyCopiesBytes() {
        byte[] bytes={1,2}; var first=new Content(null,bytes); var same=new Content(null,new byte[]{1,2});
        bytes[0]=8; first.bytes()[0]=9;
        assertThat(first).isEqualTo(same).hasSameHashCodeAs(same).isNotEqualTo(new Content(null,new byte[]{2,1}));
        assertThat(first.equals(null)).isFalse(); assertThat(first.toString()).contains("bytesHash=");
        var empty=new Content(null,null);
        assertThat(empty.bytes()).isNull(); assertThat(empty).isEqualTo(new Content(null,null));
    }
}
