CREATE SCHEMA IF NOT EXISTS customer_accounts_detail;
CREATE TABLE IF NOT EXISTS customer_accounts_detail.accounts(
      CUSTOMER_ID           varchar(30)   NOT NULL,
      ACCOUNT_NUMBER        varchar(20) 	NOT NULL,
      ACCOUNT_TYPE   	    varchar(20) 	NOT NULL,
      ROUTING_NUMBER        varchar(20)   NOT NULL,
      ACCOUNT_BALANCE       double       	NOT NULL,
      ACCOUNT_STATUS        varchar(10)   NOT NULL,
      FIRST_NAME            varchar(10)   NOT NULL,
      LAST_NAME             varchar(10)   NOT NULL,
      ACCOUNT_CREATION_DATE timestamp     NOT NULL,
      ACCOUNT_CLOSURE_DATE  timestamp     NULL,
      LAST_ACCESS_TIME      timestamp     NOT NULL
);
CREATE INDEX IF NOT EXISTS CUST_ID_IDX ON customer_accounts_detail.accounts(CUSTOMER_ID);
CREATE INDEX IF NOT EXISTS ACCT_NUM_IDX ON customer_accounts_detail.accounts(ACCOUNT_NUMBER);
CREATE INDEX IF NOT EXISTS RTNG_NUM_IDX ON customer_accounts_detail.accounts(ROUTING_NUMBER);
CREATE INDEX IF NOT EXISTS ACCT_STAT_IDX ON customer_accounts_detail.accounts(ACCOUNT_STATUS);
CREATE INDEX IF NOT EXISTS LST_ACCS_TM_IDX ON customer_accounts_detail.accounts(LAST_ACCESS_TIME);
