package com.bko.orchestration.service;

import com.bko.entity.ExternalDocument;
import com.bko.repository.ExternalDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ExternalDocumentService {

    private final ExternalDocumentRepository repository;

    @Transactional
    public ExternalDocument upsert(ExternalDocument incoming) {
        if (incoming == null || !StringUtils.hasText(incoming.getSource()) || !StringUtils.hasText(incoming.getSourceId())) {
            throw new IllegalArgumentException("ExternalDocument requires source and sourceId.");
        }
        Optional<ExternalDocument> existing = repository.findBySourceAndSourceId(incoming.getSource(), incoming.getSourceId());
        if (existing.isEmpty()) {
            return repository.save(incoming);
        }
        ExternalDocument target = existing.get();
        target.setTitle(incoming.getTitle());
        target.setAbstractText(incoming.getAbstractText());
        target.setUrl(incoming.getUrl());
        target.setAuthors(incoming.getAuthors());
        target.setCategories(incoming.getCategories());
        target.setSourcePublishedAt(incoming.getSourcePublishedAt());
        target.setSourceUpdatedAt(incoming.getSourceUpdatedAt());
        return repository.save(target);
    }

    public Set<String> findExistingSourceIds(String source, Collection<String> sourceIds) {
        if (!StringUtils.hasText(source) || sourceIds == null || sourceIds.isEmpty()) {
            return Set.of();
        }
        var ids = sourceIds.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(repository.findExistingSourceIds(source, ids));
    }
}
