package com.djmahirnationtv.status.backend.team;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {
    List<TeamMember> findByOwnerIdOrderByIdDesc(Long ownerId);
    List<TeamMember> findByMemberIdOrderByIdDesc(Long memberId);
    Optional<TeamMember> findByOwnerIdAndMemberIdAndAcceptedTrue(Long ownerId, Long memberId);
    boolean existsByOwnerIdAndMemberId(Long ownerId, Long memberId);
    long countByOwnerId(Long ownerId);
}
