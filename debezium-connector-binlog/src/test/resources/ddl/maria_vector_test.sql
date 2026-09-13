-- This relies upon MariaDB 11.7's vector datatype.

CREATE TABLE dbz_8157 (
  id INT AUTO_INCREMENT NOT NULL,
  f_vector_null VECTOR(2) DEFAULT NULL,
  f_vector_default VECTOR(2) DEFAULT NULL,
  f_vector_cons VECTOR(2) DEFAULT NULL,
  PRIMARY KEY (id)
) DEFAULT CHARSET=utf8;
INSERT INTO dbz_8157 VALUES (default, Vec_FromText('[1.1,2.2]'),Vec_FromText('[11.5,22.6]'),Vec_FromText('[31,32]'));
