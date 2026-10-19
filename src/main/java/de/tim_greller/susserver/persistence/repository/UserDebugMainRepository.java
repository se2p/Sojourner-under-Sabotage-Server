package de.tim_greller.susserver.persistence.repository;

import de.tim_greller.susserver.persistence.entity.UserDebugMainEntity;
import org.springframework.stereotype.Repository;

@Repository
public interface UserDebugMainRepository extends UserComponentScopedRepository<UserDebugMainEntity> {
}
