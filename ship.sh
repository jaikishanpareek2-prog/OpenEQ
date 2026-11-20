#!/data/data/com.termux/files/usr/bin/bash
cd ~/openeq
R=jaikishanpareek2-prog/OpenEQ
T=$(cat ~/.openeq_token)
A="Authorization: Bearer $T"
D=$HOME/.tmpeq; mkdir -p $D
git add -A; git commit -q -m "${1:-update}" 2>/dev/null
git push -q https://jaikishanpareek2-prog:$T@github.com/$R.git main || exit 1
S=$(git rev-parse HEAD)
echo "Building on GitHub, please wait..."
sleep 10
while true; do
  J=$(curl -s -H "$A" "https://api.github.com/repos/$R/actions/runs?head_sha=$S")
  [ "$(echo "$J" | jq -r '.workflow_runs[0].status')" = "completed" ] && break
  printf "."; sleep 10
done
echo
ID=$(echo "$J" | jq -r '.workflow_runs[0].id')
if [ "$(echo "$J" | jq -r '.workflow_runs[0].conclusion')" = "success" ]; then
  U=""
  for i in 1 2 3 4 5 6; do
    U=$(curl -s -H "$A" https://api.github.com/repos/$R/actions/runs/$ID/artifacts | jq -r '.artifacts[0].archive_download_url // empty')
    [ -n "$U" ] && break
    sleep 5
  done
  [ -z "$U" ] && { echo "APK link not ready. Run ship.sh again."; exit 1; }
  L=$(curl -s -D - -o /dev/null -H "$A" "$U" | grep -i '^location:' | tr -d '\r' | cut -d' ' -f2-)
  [ -z "$L" ] && { echo "GitHub gave no download link:"; curl -s -H "$A" "$U" | head -c 300; exit 1; }
  curl -sL "$L" -o $D/a.zip
  rm -rf ~/openeq/out; unzip -oq $D/a.zip -d ~/openeq/out
  if cp ~/openeq/out/*.apk ~/OpenEQ.apk 2>/dev/null; then
    cp ~/OpenEQ.apk ~/storage/downloads/ 2>/dev/null
    echo "Done! APK saved as ~/OpenEQ.apk"
  else
    echo "APK file not found in the download."
  fi
else
  curl -sL -H "$A" https://api.github.com/repos/$R/actions/runs/$ID/logs -o $D/l.zip
  rm -rf $D/l; unzip -oq $D/l.zip -d $D/l
  grep -rhE "e: file|error:|What went wrong" -A2 $D/l | head -40
fi
