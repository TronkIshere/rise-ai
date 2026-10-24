ALTER TABLE campaigns
    ADD COLUMN fundraising_closed_at DATETIME(6) NULL AFTER end_at,
    ADD INDEX idx_campaigns_status_end (campaign_status, end_at);
