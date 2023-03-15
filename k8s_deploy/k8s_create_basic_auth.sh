#echo <secret> | gpg -r manimaul@gmail.com --armor --encrypt
password=$(cat <<EOM
-----BEGIN PGP MESSAGE-----

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
EOM
)

password=$(echo "$password" | gpg -d 2>/dev/null)
kubectl -n njord create secret generic haproxy-credentials \
        --from-literal=admin=$(openssl passwd -1 $password) \
