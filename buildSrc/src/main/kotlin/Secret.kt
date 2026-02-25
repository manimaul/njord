import java.util.*

//echo <secret> | gpg -r manimaul@gmail.com --armor --encrypt
val password_enc="""-----BEGIN PGP MESSAGE-----

hQIMA5AJyFwTNT3gAQ//YNhtRJ8J94tIbJ5wIiMjT06K4rNUEFBK9llBzFVVOK15
AHRMqNwoO77wYUC3jPQfRLSenwM1+r3CUHtlTzD1as93p4fRpNJG8oR8GAmQBFUN
vT1nbFklG3xXlLNwvYXXV7k8nk2LzgxGDNZ2niYvR5AN5odZrcD6K8h6ClL8bYnR
iwarDT5JWThhVQ0w3yKcV8ZCETqm1dZFqWxP77tjm3PqrQCAqMjXgJUc7oju4asF
uVmXi4zRENE0thQoTL0wUtdfUXhzlucAh3iZV8Ip/JJ74zjKTkvz17F0AJh91MOz
c65dbwQlGgW/KKVLuZGI7s5uDM4IuylSXtI/te7cxQ7JJOgrf1DWvKWj59WFI9zg
V0Q9Hw9xdNGWkWoQ2ubxuFgAPz7W8cvXp/yN5m2AGO2nEH1MW9NHdBa7k6lkW2at
SahIZ4uQYBOcQODMjss8SrZs0EJ5gnaJMZk+3A4qJ+oKGBJfytc07VDQmrWfKNQR
dcGyRomtUo9U0LkZn+O9ssyT31Vxda7OCZqwVGDwRjhHOszxC6BelpLad+MEqNsT
9mRlr/5ge0TCa269MPMZK55rS8zmfv4sl/bdoiXz0zXPMG2+TuCFv82g0yomR6em
pCXKGlABlY/7795CmGega1fITJMPr35HL20PZ5Ej97PGetNSuLG8koowbitT+kbS
SgFtkfkXjbEiaKDTIuF9KTNa6/6dKaj1TU2UTDzI9q+j5J2LC5OYVeznLIbX7J1K
aeVDTD7f/UuLJdcIZ4nUBBxirJDBsnn14t80
=8eCg
-----END PGP MESSAGE-----
"""

fun password() = CommandLine.exec("echo '${password_enc}' | gpg -d 2>/dev/null")

val user_enc="""-----BEGIN PGP MESSAGE-----

hQIMA5AJyFwTNT3gAQ//Tj+063ivqocRja8wwXV4MAT6TpRCriPnUYYraHKE41w4
UnrnkGMehCUAmG1T3EYMAMArreO8dy3b5pzQP/WMdrskgs8hRaHqBQFid/oLhHSo
C9FIGg3Iu/egWPse03krdkCqmOjjA5kt2mhR11X6JbtnqbSYQtGh4RKxivIMbkck
cEc0vlQ1w75DQdtuPIkGm73ofibEIA4nzbsZ4KPtYzAyPpDVzPDB8KiaOxRnt40a
m/0J6m6HbbadMnaj+B59ON8oSQdfMM+wn8lk3qdLW6rQ1fYn6K3rjpZ0BZKQKiZs
fEGz6nJoHstzfdBXIe/fp1lWBjSRYY2Q1dohq373x41pjAHKbSrz+1e6Zcr//Zh6
+i+nWwVWHewinsMQ9z6lzRyoA5UGaqfMq1CmWRWxqoxpSSxjCxErN2JBCcS4DNHZ
gwU0g8n0I6d89lv4SseE5jha0BxlljA+koKIuWSt1jrKFoP919XqidmGrUj1q/uQ
aqRPU5TszUnnQ3+cVwXNKr1sDVGXhwl8wQ6DiNlEJ20tJ59yczseNSMOZhYedap2
TqPfhgRfFednrrQ12CNBUyRIWjcD0eFDkNdo+o3mIHwOfZhtmVgqWvYjpUsXkBN+
n9xv3gPofJTS6v41LerCjFOngplIXBKUYZkDhq8x8nkaSGPxNdauZogw+R1MXObS
QwEH+WH4WvMduCud98tTQYGfcXiQo3lpp+uBqHkLc4v1+037ksU/4k7VBUV8f6K0
vc1vp0VVC6WbH9C8T1IiCFMqCMw=
=crS7
-----END PGP MESSAGE-----
"""

fun user() = CommandLine.exec("echo '${user_enc}' | gpg -d 2>/dev/null")

val admin_key_enc="""-----BEGIN PGP MESSAGE-----

hQIMA5AJyFwTNT3gAQ/8DJcfttl2BHTWUqKqk5e0b1sbWbmowUwuLN0yq5DhGw4L
SZBRsYvK6KTI3Bbk2QSlDF4NCM4Jm+PLkP6l/sxgE42ALsx0LecG9F7EqRVUqPNW
X3Df07JfWAJsRfQ7BJeovRRqxen/e4lkkgzwG3IP6/K9X/9oOyK3k4FYUd1O++qw
EjOrhjuQIcZhtzLDznP0qL00wfODkhTht4re6Qd4NNl2NCCImdoU/K9g+ut/ZEJU
x5IJqKGWd49hqZ/BQxShvthjbMx5IGS7uKL2vhe1q6usp+Ev6TsYoTK6oA4psP4G
raw+nKyIBX3eXmprNCI8XHFsx7xhX25qcndKJsq07TIT/4SX8rWcYV2NlgJl8rpv
phiMgn1ndtH8irMGaPhKIwJlMDzoWRH1WJdFa7uRBgU8V+mwNAeVMbU6LCWa8M5a
9RshLjCK1XcFnicWL8OOZOrE93W0Mw/xLfqwcrsIEJ+NAQyv1qBjZUBSiWyKsfz2
fGsyltOOIybbYD2kgNetaGyXZkN60YfU0V/W+gM2qyoDuIWeZBO1FiT3HqyTF7NN
BA6tAGqdwxtDS9kRv6ei5l1ifd3fmQ6H1Oa5XfeJQabQdr8drXFkBZCzHLe4KF+v
fRCqDVSyK7dbzsnb1Se5afTMcJXse8sCl+V8+8x99sWG5aXSWnn0VB78aFOBLJzS
YAH+OnPDIqz4DT5J3Ufk3Jn5ezauPaNcERHXCvriGLyc+J+q4fuKcCl6kghzMlcA
aJFDBGfAbkvGF//fQT4QhTGj4wh5b/zzf0WLcWjXTTAxQrjbnQPoY5caoYdOIjnC
4Q==
=BPRm
-----END PGP MESSAGE-----
"""

fun adminKey() = CommandLine.exec("echo '${admin_key_enc}' | gpg -d 2>/dev/null")

fun options() = """
    {
    "adminKey": "${adminKey()}",
    "adminUser": "${user()}",
    "adminPass": "${password()}"
    }
""".trimIndent()

fun secretYaml() = """apiVersion: v1
kind: Secret
metadata:
  name: admin-secret-json
  namespace: njord
data:
  chart_server_opts: "${Base64.getEncoder().encodeToString(options().toByteArray())}" 
"""

fun k8sApplySecret() = CommandLine.exec("echo '${secretYaml()}' | kubectl apply -f -")
