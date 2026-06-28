-- V011: add package_allowance to t_billing_rule for BY_PACKAGE model support.

ALTER TABLE t_billing_rule ADD COLUMN package_allowance BIGINT NOT NULL DEFAULT 0;
