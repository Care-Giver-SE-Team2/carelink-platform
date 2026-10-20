package sg.nus.carelink.visit.domain.repository;

import java.util.*;
import sg.nus.carelink.visit.domain.model.VisitEvidence;
import sg.nus.carelink.visit.domain.model.VisitEvidenceFile;

public interface VisitEvidenceFileStore {
    record Entry(VisitEvidence evidence,VisitEvidenceFile file) { }
    List<Entry> list(Long visitId,int offset,int size);
    Optional<Entry> find(Long visitId,Long evidenceId);
    long count(Long visitId);
    long verifiedCount(Long visitId);
    long fileCount(Long visitId);
    void save(VisitEvidenceFile file);
}
