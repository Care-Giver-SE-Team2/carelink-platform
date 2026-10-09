-- Corrections to a generated report (UC-MG07). A report is archived as it was generated and
-- is never edited or deleted afterwards ("已归档的报告不可删除、不可原地修改，更正只能以附加说明的
-- 形式追加"), so a correction is a dated, signed note in a table of its own. Nothing here is ever
-- updated either: a correction to a correction is another row.
--
-- The report row itself is not touched. Whether its data was complete, what was missing and
-- how it was generated all live in report.content, the JSON the report was archived with.
CREATE TABLE report_amendment (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    report_id       BIGINT        NOT NULL,
    note            VARCHAR(1000) NOT NULL,
    author_user_id  BIGINT        NOT NULL COMMENT 'soft FK to app_user.id; every correction is signed',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_report_amendment_report (report_id, created_at),
    CONSTRAINT fk_report_amendment_report FOREIGN KEY (report_id) REFERENCES report (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
