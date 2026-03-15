package com.bko.repository;

import com.bko.entity.ExternalDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExternalDocumentRepository extends JpaRepository<ExternalDocument, UUID> {

    Optional<ExternalDocument> findBySourceAndSourceId(String source, String sourceId);

    long countBySource(String source);

    List<ExternalDocument> findByOrderBySourcePublishedAtDescCreatedAtDesc(
            org.springframework.data.domain.Pageable pageable
    );

    List<ExternalDocument> findBySourcePublishedAtGreaterThanEqualOrderBySourcePublishedAtDescCreatedAtDesc(
            OffsetDateTime from,
            org.springframework.data.domain.Pageable pageable
    );

    List<ExternalDocument> findBySourcePublishedAtLessThanEqualOrderBySourcePublishedAtDescCreatedAtDesc(
            OffsetDateTime to,
            org.springframework.data.domain.Pageable pageable
    );

    List<ExternalDocument> findBySourcePublishedAtBetweenOrderBySourcePublishedAtDescCreatedAtDesc(
            OffsetDateTime from,
            OffsetDateTime to,
            org.springframework.data.domain.Pageable pageable
    );

    /**
     * Native query with CAST to TEXT so columns stored as BYTEA (e.g. from older schema)
     * still work with lower() in PostgreSQL.
     */
    @Query(value = """
            SELECT ed.* FROM external_document ed
            WHERE (:source IS NULL OR lower(CAST(ed.source AS TEXT)) = lower(CAST(:source AS TEXT)))
              AND (
                    :queryText IS NULL
                    OR lower(CAST(COALESCE(ed.title, '') AS TEXT)) LIKE lower('%' || CAST(:queryText AS TEXT) || '%')
                    OR lower(CAST(COALESCE(ed.abstract_text, '') AS TEXT)) LIKE lower('%' || CAST(:queryText AS TEXT) || '%')
                    OR lower(CAST(COALESCE(ed.authors, '') AS TEXT)) LIKE lower('%' || CAST(:queryText AS TEXT) || '%')
                    OR lower(CAST(COALESCE(ed.categories, '') AS TEXT)) LIKE lower('%' || CAST(:queryText AS TEXT) || '%')
                    OR lower(CAST(COALESCE(ed.source_id, '') AS TEXT)) LIKE lower('%' || CAST(:queryText AS TEXT) || '%')
                  )
            ORDER BY ed.source_published_at DESC, ed.created_at DESC
            """, nativeQuery = true)
    List<ExternalDocument> searchDocuments(
            @Param("source") String source,
            @Param("queryText") String queryText,
            org.springframework.data.domain.Pageable pageable
    );

    /**
     * Native count with CAST to TEXT for BYTEA-safe lower() on PostgreSQL.
     */
    @Query(value = """
            SELECT COUNT(*) FROM external_document ed
            WHERE (:source IS NULL OR lower(CAST(ed.source AS TEXT)) = lower(CAST(:source AS TEXT)))
              AND (
                    :queryText IS NULL
                    OR lower(CAST(COALESCE(ed.title, '') AS TEXT)) LIKE lower('%' || CAST(:queryText AS TEXT) || '%')
                    OR lower(CAST(COALESCE(ed.abstract_text, '') AS TEXT)) LIKE lower('%' || CAST(:queryText AS TEXT) || '%')
                    OR lower(CAST(COALESCE(ed.authors, '') AS TEXT)) LIKE lower('%' || CAST(:queryText AS TEXT) || '%')
                    OR lower(CAST(COALESCE(ed.categories, '') AS TEXT)) LIKE lower('%' || CAST(:queryText AS TEXT) || '%')
                    OR lower(CAST(COALESCE(ed.source_id, '') AS TEXT)) LIKE lower('%' || CAST(:queryText AS TEXT) || '%')
                  )
            """, nativeQuery = true)
    long countSearchDocuments(
            @Param("source") String source,
            @Param("queryText") String queryText
    );

    /**
     * Native query with CAST so source column works even if stored as BYTEA.
     */
    @Query(value = """
            SELECT ed.source_id FROM external_document ed
            WHERE lower(CAST(ed.source AS TEXT)) = lower(CAST(:source AS TEXT))
              AND ed.source_id IN :sourceIds
            """, nativeQuery = true)
    List<String> findExistingSourceIds(
            @Param("source") String source,
            @Param("sourceIds") List<String> sourceIds
    );
}
