package de.tim_greller.susserver.persistence.repository;

import de.tim_greller.susserver.persistence.entity.ActivePatchEntity;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ActivePatchRepository
        extends UserComponentScopedRepository<ActivePatchEntity>, JpaSpecificationExecutor<ActivePatchEntity> {
}
