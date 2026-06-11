package de.tim_greller.susserver.persistence.repository;

import java.util.List;
import java.util.Optional;

import de.tim_greller.susserver.dto.GameMode;
import de.tim_greller.susserver.persistence.entity.GameProgressionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GameProgressionRepository extends
        JpaRepository<GameProgressionEntity, Integer>,
        JpaSpecificationExecutor<GameProgressionEntity> {

    @Query("SELECT gp FROM GameProgressionEntity gp WHERE gp.mode = :mode ORDER BY gp.orderIndex LIMIT 1")
    Optional<GameProgressionEntity> findFirstProgression(@Param("mode") GameMode mode);

    @Query("SELECT gp FROM GameProgressionEntity gp WHERE gp.mode = :mode AND gp.orderIndex > :afterOrderIndex "
            + "ORDER BY gp.orderIndex LIMIT 1")
    Optional<GameProgressionEntity> findNextProgression(@Param("mode") GameMode mode,
                                                        @Param("afterOrderIndex") int afterOrderIndex);

    @Query("SELECT DISTINCT gp.component.name FROM GameProgressionEntity gp WHERE gp.mode = :mode")
    List<String> findComponentNamesByMode(@Param("mode") GameMode mode);
}