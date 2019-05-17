CREATE SCHEMA IF NOT EXISTS customer_accounts_detail;
CREATE TABLE IF NOT EXISTS customer_accounts_detail.address(
      CUSTOMER_ID       varchar(30)   NOT NULL,
      ADDRESS1          varchar(100)  NOT NULL,
      ADDRESS2          varchar(100)  NULL,
      CITY              varchar(30)   NOT NULL,
      STATE             varchar(20)   NOT NULL
      COUNTRY           varchar(30)   NOT NULL,
      ZIP               int           NOT NULL
);
CREATE INDEX IF NOT EXISTS CUST_ID_IDX ON customer_accounts_detail.address(CUSTOMER_ID);
CREATE INDEX IF NOT EXISTS ZIP_IDX ON customer_accounts_detail.address(ZIP);
