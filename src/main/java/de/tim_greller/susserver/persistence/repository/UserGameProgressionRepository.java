package de.tim_greller.susserver.persistence.repository;

import de.tim_greller.susserver.dto.GameMode;
import de.tim_greller.susserver.persistence.entity.UserGameProgressionEntity;
import de.tim_greller.susserver.persistence.keys.UserModeKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface UserGameProgressionRepository extends
        JpaRepository<UserGameProgressionEntity, UserModeKey>,
        JpaSpecificationExecutor<UserGameProgressionEntity> {

    // Existence check without loading the UserEntity for a full UserModeKey
    boolean existsByIdUserUsernameAndIdMode(String username, GameMode mode);
}