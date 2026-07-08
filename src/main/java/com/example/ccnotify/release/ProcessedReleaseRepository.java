package com.example.ccnotify.release;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProcessedReleaseRepository extends JpaRepository<ProcessedRelease, String> {

    @Query("select p.version from ProcessedRelease p")
    List<String> findAllVersions();
}
