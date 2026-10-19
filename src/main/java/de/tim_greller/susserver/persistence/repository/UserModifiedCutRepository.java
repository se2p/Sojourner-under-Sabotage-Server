package de.tim_greller.susserver.persistence.repository;

import de.tim_greller.susserver.persistence.entity.UserModifiedCutEntity;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface UserModifiedCutRepository extends
        UserComponentScopedRepository<UserModifiedCutEntity>,
        JpaSpecificationExecutor<UserModifiedCutEntity> {

    @Transactional
    @Modifying
    @Query("""
            DELETE FROM UserModifiedCutEntity u
            WHERE u.userComponentKey.component.name = :componentName
            AND u.userComponentKey.user.username = :userId
            """)
    void deleteByKey(String componentName, String userId);
}
