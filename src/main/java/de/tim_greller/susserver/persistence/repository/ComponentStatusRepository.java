package de.tim_greller.susserver.persistence.repository;

import de.tim_greller.susserver.persistence.entity.ComponentStatusEntity;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ComponentStatusRepository extends
        UserComponentScopedRepository<ComponentStatusEntity>,
        JpaSpecificationExecutor<ComponentStatusEntity> {
}
