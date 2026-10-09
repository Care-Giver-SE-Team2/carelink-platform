package sg.nus.carelink.profile.infrastructure.persistence.adapter;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;
import sg.nus.carelink.profile.domain.model.ElderBasicDetails;
import sg.nus.carelink.profile.domain.model.ServiceApplication;
import sg.nus.carelink.profile.domain.model.ServiceApplicationPage;
import sg.nus.carelink.profile.domain.repository.ServiceApplicationRepository;
import sg.nus.carelink.profile.infrastructure.persistence.entity.ServiceApplicationJpaEntity;
import sg.nus.carelink.profile.infrastructure.persistence.repository.ServiceApplicationJpaRepository;

@Repository
class ServiceApplicationRepositoryAdapter implements ServiceApplicationRepository {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final ServiceApplicationJpaRepository jpa;

    ServiceApplicationRepositoryAdapter(ServiceApplicationJpaRepository jpa) { this.jpa = jpa; }

    @Override
    public ServiceApplication save(ServiceApplication application) {
        var entity = new ServiceApplicationJpaEntity();
        entity.setId(application.id());
        entity.setApplicantFamilyMemberId(application.applicantFamilyMemberId());
        entity.setElderId(application.elderId());
        entity.setElderSnapshot(JSON.writeValueAsString(application.elderSnapshot()));
        entity.setCareNeeds(application.careNeeds());
        entity.setNotes(application.notes());
        entity.setStatus(application.status().name());
        entity.setCreatedAt(application.createdAt());
        return toDomain(jpa.saveAndFlush(entity));
    }

    @Override
    public Optional<ServiceApplication> findById(Long id) { return jpa.findById(id).map(this::toDomain); }

    @Override
    public ServiceApplicationPage findForApplicant(Long familyId, Set<Long> readableElderIds, int page, int size) {
        if (readableElderIds.isEmpty()) return new ServiceApplicationPage(List.of(), page, size, 0);
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        long total = jpa.countByApplicantFamilyMemberIdAndElderIdIn(familyId, readableElderIds);
        if (pageable.getOffset() >= total) return new ServiceApplicationPage(List.of(), page, size, total);
        var items = jpa.findByApplicantFamilyMemberIdAndElderIdIn(familyId, readableElderIds, pageable);
        return new ServiceApplicationPage(items.stream().map(this::toDomain).toList(), page, size, total);
    }

    private ServiceApplication toDomain(ServiceApplicationJpaEntity entity) {
        return new ServiceApplication(entity.getId(), entity.getApplicantFamilyMemberId(), entity.getElderId(),
                JSON.readValue(entity.getElderSnapshot(), ElderBasicDetails.class), entity.getCareNeeds(), entity.getNotes(),
                ServiceApplication.Status.valueOf(entity.getStatus()), entity.getCreatedAt());
    }
}
