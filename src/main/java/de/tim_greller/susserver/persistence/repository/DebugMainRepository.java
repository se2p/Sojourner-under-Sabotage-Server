package de.tim_greller.susserver.persistence.repository;

import java.util.Optional;

import de.tim_greller.susserver.persistence.entity.DebugMainEntity;
import de.tim_greller.susserver.persistence.keys.ComponentStageKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface DebugMainRepository extends JpaRepository<DebugMainEntity, ComponentStageKey> {

    @Query("""
            SELECT d
            FROM DebugMainEntity d
            WHERE d.componentStageKey.component.name = :componentName
            AND d.componentStageKey.stage = :stage
            """)
    Optional<DebugMainEntity> findByKey(String componentName, int stage);
}