package com.apicia.repository;

import com.apicia.model.entity.ClientProject;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClientProjectRepository extends JpaRepository<ClientProject, Long> {
    Optional<ClientProject> findByProjectName(String projectName);
}
