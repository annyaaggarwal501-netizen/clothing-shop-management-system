#!/bin/bash
# Before-demo check: "Two people can't both buy the last piece at the same time" (REQ-INV-5).
# Two staff sessions try to sell the same piece at the same moment. Exactly one must succeed;
# the other must get ERR-INV-001. Run on Windows from Git Bash, or on Mac/Linux.
#
# Usage: scripts/race_test.sh [barcode]   (default: Floral Cotton Kurti XL/Pink, 1 piece in the seed data)
BASE=${BASE:-http://localhost:8080/api/v1}
BARCODE=${1:-2000000000084}
TMP=$(mktemp -d)

login() { curl -s -c "$TMP/$1.jar" -H 'Content-Type: application/json' -X POST "$BASE/auth/login" \
               -d '{"username":"staff","password":"Staff@123"}' > /dev/null; }
login a; login b

V=$(curl -s -b "$TMP/a.jar" "$BASE/variants?barcode=$BARCODE")
ID=$(echo "$V" | sed -n 's/.*"id":\([0-9]*\).*/\1/p' | head -1)
PRICE=$(echo "$V" | sed -n 's/.*"sellingPrice":\([0-9.]*\).*/\1/p')
STOCK=$(echo "$V" | sed -n 's/.*"stockQty":\([0-9]*\).*/\1/p')
echo "Item $ID costs Rs $PRICE, stock before: $STOCK"
if [ "$STOCK" != "1" ]; then echo "Pick an item with exactly 1 piece left (or rebuild the database)."; exit 1; fi

BODY="{\"lines\":[{\"variantId\":$ID,\"quantity\":1}],\"payments\":[{\"mode\":\"CASH\",\"amount\":$PRICE}]}"
curl -s -b "$TMP/a.jar" -H 'Content-Type: application/json' -X POST "$BASE/sales" -d "$BODY" > "$TMP/a.out" &
curl -s -b "$TMP/b.jar" -H 'Content-Type: application/json' -X POST "$BASE/sales" -d "$BODY" > "$TMP/b.out" &
wait

echo "Counter A: $(cut -c1-120 "$TMP/a.out")"
echo "Counter B: $(cut -c1-120 "$TMP/b.out")"
OK=$(grep -l invoiceNo "$TMP"/*.out | wc -l)
FAIL=$(grep -l ERR-INV-001 "$TMP"/*.out | wc -l)
if [ "$OK" = "1" ] && [ "$FAIL" = "1" ]; then echo "PASS: one sale, one ERR-INV-001"; else echo "FAIL"; exit 1; fi
