XA_PREPARED_LIST 'transaction-1';

CREATE TABLE inventory.customers (id INT NOT NULL) DUPLICATE='Y';
CREATE UNIQUE INDEX inventory.idx_customers ON inventory.customers(id);
DROP INDEX inventory.idx_customers ON inventory.customers;
DROP INDEX inventory.idx_customers;

TRUNCATE TABLE inventory.customers FOR RECYCLEBIN_VERSION 'v1';
DROP TABLE inventory.customers FOR RECYCLEBIN_VERSION 'v1';
PURGE TABLE inventory.customers FOR RECYCLEBIN_VERSION 'v1';

RENAME inventory.old_name TO inventory.new_name;
