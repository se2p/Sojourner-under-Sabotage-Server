package de.tim_greller.susserver.persistence.repository;

import java.util.Collection;
import java.util.Optional;

import de.tim_greller.susserver.persistence.keys.UserComponentKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * Base repository for entities keyed by a {@link UserComponentKey} field named {@code userComponentKey}.
 * The {@code #{#entityName}} placeholder resolves to the extending repository's entity.
 */
@NoRepositoryBean
public interface UserComponentScopedRepository<T> extends JpaRepository<T, UserComponentKey> {

    @Query("""
            SELECT e
            FROM #{#entityName} e
            WHERE e.userComponentKey.component.name = :componentName
            AND e.userComponentKey.user.username = :userId
            """)
    Optional<T> findByKey(String componentName, String userId);

    @Transactional
    @Modifying
    @Query("""
            DELETE FROM #{#entityName} e
            WHERE e.userComponentKey.user.username = :userId
            AND e.userComponentKey.component.name IN :componentNames
            """)
    void deleteAllByUserAndComponents(String userId, Collection<String> componentNames);
}
