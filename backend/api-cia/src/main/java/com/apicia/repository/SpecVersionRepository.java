package com.apicia.repository;

import com.apicia.model.entity.SpecVersion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpecVersionRepository extends JpaRepository<SpecVersion, Long> {

    List<SpecVersion> findAllByOrderByUploadedAtDesc();
}
