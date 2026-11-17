package com.eqima.keacloakadmin.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.eqima.keacloakadmin.models.entity.RoleSodRule;

public interface RoleSodRuleRepository extends JpaRepository<RoleSodRule, UUID> {

    // les deux sens — une règle peut être posée dans n'importe quel ordre
    @Query("""
        select r from RoleSodRule r
        where (r.clientUuidA = :clientUuid and r.roleNameA = :roleName)
           or (r.clientUuidB = :clientUuid and r.roleNameB = :roleName)
    """)
    List<RoleSodRule> findAllInvolving(@Param("clientUuid") String clientUuid, @Param("roleName") String roleName);

    @Query("""
        select (count(r) > 0) from RoleSodRule r
        where (r.clientUuidA = :ca and r.roleNameA = :na and r.clientUuidB = :cb and r.roleNameB = :nb)
           or (r.clientUuidA = :cb and r.roleNameA = :nb and r.clientUuidB = :ca and r.roleNameB = :na)
    """)
    boolean existsBetween(@Param("ca") String clientUuidA, @Param("na") String roleNameA,
                          @Param("cb") String clientUuidB, @Param("nb") String roleNameB);
}
