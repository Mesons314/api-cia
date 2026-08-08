package com.apicia.repository;

import com.apicia.model.entity.ClientDependency;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClientDependencyRepository extends JpaRepository<ClientDependency, Long> {
    List<ClientDependency> findByHttpMethodIgnoreCaseAndNormalizedPath(String httpMethod, String normalizedPath);
    void deleteByClientProjectId(Long clientProjectId);
}
