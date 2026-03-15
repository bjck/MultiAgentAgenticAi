import React, { useEffect, useState } from 'react';
import '../styles/ArxivView.css';

const API_BASE = '/api';

function formatDate(iso) {
  if (!iso) return '—';
  try {
    const d = new Date(iso);
    return d.toLocaleDateString(undefined, { dateStyle: 'short' }) + ' ' + d.toLocaleTimeString(undefined, { timeStyle: 'short' });
  } catch {
    return iso;
  }
}

function ArxivView() {
  const [data, setData] = useState({ content: [], total: 0, page: 0, size: 50 });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [page, setPage] = useState(0);
  const [query, setQuery] = useState('');
  const [searchInput, setSearchInput] = useState('');
  const size = 50;

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    const params = new URLSearchParams({ source: 'arxiv', page: String(page), size: String(size) });
    if (query.trim()) params.set('q', query.trim());
    fetch(`${API_BASE}/documents?${params}`)
      .then((res) => {
        if (!res.ok) throw new Error(res.statusText || 'Failed to load documents');
        return res.json();
      })
      .then((body) => {
        if (!cancelled) {
          setData(body);
          setLoading(false);
        }
      })
      .catch((err) => {
        if (!cancelled) {
          setError(err.message);
          setLoading(false);
        }
      });
    return () => { cancelled = true; };
  }, [page, query]);

  const handleSearch = (e) => {
    e.preventDefault();
    setQuery(searchInput);
    setPage(0);
  };

  const totalPages = data.size > 0 ? Math.ceil(data.total / data.size) : 0;
  const from = data.total === 0 ? 0 : data.page * data.size + 1;
  const to = Math.min((data.page + 1) * data.size, data.total);

  return (
    <div className="arxiv-view">
      <div className="arxiv-view-hero">
        <p className="arxiv-view-eyebrow">External docs</p>
        <h2>arXiv stored abstracts</h2>
        <p className="arxiv-view-subtitle">
          Papers ingested by the arxiv_api_reader tool. Search and browse by title, authors, categories, or abstract.
        </p>
      </div>

      <div className="arxiv-view-toolbar">
        <form onSubmit={handleSearch} className="arxiv-view-search">
          <input
            type="search"
            placeholder="Search title, authors, categories, abstract…"
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            className="arxiv-view-search-input"
            aria-label="Search arxiv documents"
          />
          <button type="submit" className="arxiv-view-search-btn">Search</button>
        </form>
        {query && (
          <button
            type="button"
            className="arxiv-view-clear"
            onClick={() => { setSearchInput(''); setQuery(''); setPage(0); }}
          >
            Clear
          </button>
        )}
      </div>

      {error && (
        <div className="arxiv-view-error" role="alert">
          {error}
        </div>
      )}

      {loading ? (
        <div className="arxiv-view-loading">Loading…</div>
      ) : (
        <>
          <div className="arxiv-view-meta">
            Showing {from}–{to} of {data.total} document{data.total === 1 ? '' : 's'}
          </div>

          {data.content.length === 0 ? (
            <div className="arxiv-view-empty">
              No arXiv documents found. Ingest papers using an agent that calls the <code>arxiv_api_reader</code> tool.
            </div>
          ) : (
            <div className="arxiv-view-list">
              {data.content.map((doc) => (
                <article key={doc.id} className="arxiv-view-card">
                  <div className="arxiv-view-card-header">
                    <span className="arxiv-view-card-id">{doc.sourceId}</span>
                    {doc.sourcePublishedAt && (
                      <time dateTime={doc.sourcePublishedAt} className="arxiv-view-card-date">
                        {formatDate(doc.sourcePublishedAt)}
                      </time>
                    )}
                  </div>
                  <h3 className="arxiv-view-card-title">
                    {doc.url ? (
                      <a href={doc.url} target="_blank" rel="noopener noreferrer">{doc.title || 'Untitled'}</a>
                    ) : (
                      doc.title || 'Untitled'
                    )}
                  </h3>
                  {doc.authors && (
                    <p className="arxiv-view-card-authors">{doc.authors}</p>
                  )}
                  {doc.categories && (
                    <p className="arxiv-view-card-categories">{doc.categories}</p>
                  )}
                  {doc.abstractText && (
                    <p className="arxiv-view-card-abstract">{doc.abstractText}</p>
                  )}
                </article>
              ))}
            </div>
          )}

          {totalPages > 1 && (
            <nav className="arxiv-view-pagination" aria-label="Pagination">
              <button
                type="button"
                disabled={page <= 0}
                onClick={() => setPage((p) => p - 1)}
              >
                Previous
              </button>
              <span className="arxiv-view-pagination-info">
                Page {page + 1} of {totalPages}
              </span>
              <button
                type="button"
                disabled={page >= totalPages - 1}
                onClick={() => setPage((p) => p + 1)}
              >
                Next
              </button>
            </nav>
          )}
        </>
      )}
    </div>
  );
}

export default ArxivView;
