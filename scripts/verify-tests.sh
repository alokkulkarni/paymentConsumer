#!/usr/bin/env bash
set -euo pipefail

BASE_SHA="$1"
HEAD_SHA="$2"

changed_main=$(git diff --name-only "$BASE_SHA" "$HEAD_SHA" -- '**/src/main/java/**/*.java' || true)
changed_test=$(git diff --name-only "$BASE_SHA" "$HEAD_SHA" -- '**/src/test/java/**/*.java' || true)

declare -A expected
declare -A has_test_change

while read -r f; do
  [[ -z "$f" ]] && continue
  module_dir=${f%%/src/main/*}
  rel_no_ext=${f%.java}
  pkg_path=${rel_no_ext#${module_dir}/src/main/java/}
  expected["$f"]="$module_dir/src/test/java/${pkg_path}Test.java|$module_dir/src/test/java/${pkg_path}Tests.java"
done <<< "$changed_main"

while read -r t; do
  [[ -z "$t" ]] && continue
  has_test_change["$t"]=1
done <<< "$changed_test"

missing=()
for src in "${!expected[@]}"; do
  IFS='|' read -r cand1 cand2 <<< "${expected[$src]}"
  if [[ -n "${has_test_change[$cand1]:-}" || -n "${has_test_change[$cand2]:-}" ]]; then
    continue
  fi
  missing+=("$src")
done

if [[ ${#missing[@]} -gt 0 ]]; then
  echo "Missing corresponding test changes for:" >&2
  for m in "${missing[@]}"; do echo "  - $m" >&2; done
  exit 2
fi

exit 0
