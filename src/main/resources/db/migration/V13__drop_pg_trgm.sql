-- Keyword search moved to Lucene BM25 (decision 5), so the trigram index and its extension are no longer used.
-- The index only cost disk space and slowed down every write to template_search.
DROP INDEX IF EXISTS idx_template_search_text;
DROP EXTENSION IF EXISTS pg_trgm;
