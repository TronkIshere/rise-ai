CREATE TRIGGER trg_ledger_no_update BEFORE UPDATE ON escrow_ledger_entries FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'escrow_ledger_entries is append-only';
CREATE TRIGGER trg_ledger_no_delete BEFORE DELETE ON escrow_ledger_entries FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'escrow_ledger_entries is append-only';
CREATE TRIGGER trg_audit_no_update BEFORE UPDATE ON audit_logs FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit_logs is append-only';
CREATE TRIGGER trg_audit_no_delete BEFORE DELETE ON audit_logs FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit_logs is append-only';
CREATE TRIGGER trg_status_history_no_update BEFORE UPDATE ON status_history FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'status_history is append-only';
CREATE TRIGGER trg_status_history_no_delete BEFORE DELETE ON status_history FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'status_history is append-only';
