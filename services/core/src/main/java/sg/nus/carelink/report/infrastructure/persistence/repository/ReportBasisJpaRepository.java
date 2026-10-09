package sg.nus.carelink.report.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import sg.nus.carelink.report.infrastructure.persistence.entity.ReportBasisJpaEntity;

/** Spring Data repository for report_basis. Used by persistence.adapter only; never exposed outwards. */
public interface ReportBasisJpaRepository extends JpaRepository<ReportBasisJpaEntity, Long> {
}
