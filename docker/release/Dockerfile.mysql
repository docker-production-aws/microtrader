FROM mysql:5.7
HEALTHCHECK --interval=3s --retries=20 CMD mysqlshow -u ${MYSQL_USER} -p${MYSQL_PASSWORD}