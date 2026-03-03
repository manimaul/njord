import java.util.*

fun getEnv(key: String) : String {
    return System.getenv(key).takeIf { !it.isNullOrBlank() } ?: run {
        println("$key is not an environment variable")
        "undefined"
    }
}

fun password() = getEnv("NJORD_ADMIN_PASS")
fun user() = getEnv("NJORD_ADMIN_USER")
fun adminKey() = getEnv("NJORD_ADMIN_KEY")

fun options() = """
    {
    "adminKey": "${adminKey()}",
    "adminUser": "${user()}",
    "adminPass": "${password()}",
    "pgConnectionInfo": "postgresql://${dbUser()}@localhost:5432/${dbName()}"
    }
""".trimIndent()

fun dbHost() = getEnv("NJORD_DB_HOST")
fun dbPort() = getEnv("NJORD_DB_PORT")
fun dbName() = getEnv("NJORD_DB_NAME")
fun dbUser() = getEnv("NJORD_DB_USER")
fun dbPass() = getEnv("NJORD_DB_PASS")

fun secretYaml() = """
---
apiVersion: v1
kind: Secret
metadata:
  name: njord-pgbouncer-ini
  namespace: njord
type: Opaque
stringData:
  pgbouncer.ini: |
    [databases]
    s57server = host=${dbHost()} port=${dbPort()} dbname=${dbName()} user=${dbUser()}

    [pgbouncer]
    listen_addr = 0.0.0.0
    listen_port = 5432
    auth_type = trust
    auth_file = /etc/pgbouncer/userlist.txt
    pool_mode = transaction
    max_client_conn = 100
    default_pool_size = 10
    server_reset_query =
---
apiVersion: v1
kind: Secret
metadata:
  name: njord-pgbouncer-userlist-txt
  namespace: njord
type: Opaque
stringData:
  userlist.txt: |
    "${dbUser()}" "${dbPass()}"
---
apiVersion: v1
kind: Secret
metadata:
  name: admin-secret-json
  namespace: njord
data:
  chart_server_opts: "${Base64.getEncoder().encodeToString(options().toByteArray())}" 
"""

fun k8sApplySecret() = CommandLine.exec("echo '${secretYaml()}' | kubectl apply -f -")
