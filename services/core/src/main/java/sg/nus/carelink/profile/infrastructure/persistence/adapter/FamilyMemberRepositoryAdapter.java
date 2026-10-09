package sg.nus.carelink.profile.infrastructure.persistence.adapter;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import sg.nus.carelink.profile.domain.model.FamilyMember;
import sg.nus.carelink.profile.domain.repository.FamilyMemberRepository;
import sg.nus.carelink.profile.infrastructure.persistence.repository.FamilyMemberJpaRepository;

/**
 * Implements the domain port with Spring Data. The dependency points infrastructure ->
 * domain, never the other way round (dependency inversion, as in identity).
 */
@Repository
class FamilyMemberRepositoryAdapter implements FamilyMemberRepository {

	private final FamilyMemberJpaRepository jpa;

	FamilyMemberRepositoryAdapter(FamilyMemberJpaRepository jpa) {
		this.jpa = jpa;
	}

	@Override
	public Optional<FamilyMember> findById(Long id) {
		return jpa.findById(id).map(FamilyMemberMapper::toDomain);
	}

	@Override
	public Optional<FamilyMember> findByUserId(Long userId) {
		return jpa.findByUserId(userId).map(FamilyMemberMapper::toDomain);
	}

	@Override
	public FamilyMember save(FamilyMember familyMember) {
		return FamilyMemberMapper.toDomain(jpa.save(FamilyMemberMapper.toEntity(familyMember)));
	}
}
