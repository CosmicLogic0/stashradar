#!/usr/bin/env bash
# Takes the CurseForge gallery screenshots in the real game (CI only).
#   mode "mod":    NeoForge 1.21.1 server with Stash Radar + LogicLib, NeoForge client with Stash Radar + LogicLib
#   mode "plugin": Paper 1.21.1 server with the Stash Radar plugin + LogicLib Plugin, client without Stash Radar
# Both: a Superflat world (Classic Flat, no structures), noon, clear weather, 1920x1080, Fancy graphics.
# The client also has stashradar-pageshots.jar (neoforge/src/pageshots), which joins the server and takes the shots.
# usage: ci/gallery.sh <mod|plugin> <out dir>
set -u
mode="$1"; out="$2"; MC=1.21.1
NF=$(grep '^neoforge_version=' gradle.properties | cut -d= -f2)
G="$PWD/game-$mode"; S="$PWD/server-$mode"; mkdir -p "$G/mods" "$S" "$out"
J21="$JAVA_HOME_21_X64/bin/java"
export LIBGL_ALWAYS_SOFTWARE=1

cp neoforge/build/pageshots/stashradar-pageshots.jar "$G/mods/"
if [ "$mode" = mod ]; then
  cp neoforge/build/libs/stashradar-neoforge-$MC-*.jar "$G/mods/"; rm -f "$G"/mods/*-sources.jar "$G"/mods/*-javadoc.jar
  cp libs/logiclib-neoforge-$MC-*.jar "$G/mods/"
fi
ls -la "$G/mods"

# ---- client install (real Mojang game + NeoForge, like a player's launcher)
ok=0; for i in 1 2 3; do portablemc --main-dir "$G" start --dry --jvm-policy mojang-then-system "neoforge::$NF" > "$out/$mode-install.log" 2>&1 && { ok=1; break; }; sleep 20; done
[ "$ok" = 1 ] || { echo "::error::client install failed"; tail -30 "$out/$mode-install.log"; exit 1; }

# ---- server: Superflat (Classic Flat), no structures, peaceful, creative, flying allowed
if [ "$mode" = mod ]; then
  curl -fsSL -o "$S/installer.jar" "https://maven.neoforged.net/releases/net/neoforged/neoforge/$NF/neoforge-$NF-installer.jar"
  (cd "$S" && "$J21" -jar installer.jar --install-server . > install.log 2>&1) || { tail -30 "$S/install.log"; exit 1; }
  mkdir -p "$S/mods"; cp "$G"/mods/stashradar-neoforge-*.jar "$G"/mods/logiclib-neoforge-*.jar "$S/mods/"
  START="bash run.sh nogui"
else
  B=$(curl -s "https://api.papermc.io/v2/projects/paper/versions/$MC/builds" | python3 -c "import json,sys; b=[x for x in json.load(sys.stdin)['builds'] if x['channel']=='default']; print(b[-1]['build'])")
  curl -fsSL -o "$S/paper.jar" "https://api.papermc.io/v2/projects/paper/versions/$MC/builds/$B/downloads/paper-$MC-$B.jar"
  mkdir -p "$S/plugins"; cp plugin-jars/*.jar "$S/plugins/"; ls -la "$S/plugins"
  START="$J21 -Xmx2G -jar paper.jar nogui"
fi
echo "eula=true" > "$S/eula.txt"
printf 'online-mode=false\nenforce-secure-profile=false\nlevel-type=minecraft\\:flat\ngenerate-structures=false\ngamemode=creative\ndifficulty=peaceful\nspawn-monsters=false\nspawn-animals=false\nspawn-npcs=false\nspawn-protection=0\nallow-flight=true\nview-distance=10\nsimulation-distance=8\nmax-tick-time=-1\nmotd=Stash Radar gallery\n' > "$S/server.properties"
python3 -c "import hashlib,uuid,json; h=bytearray(hashlib.md5(b'OfflinePlayer:Radar').digest()); h[6]=h[6]&0x0f|0x30; h[8]=h[8]&0x3f|0x80; print(json.dumps([{'uuid':str(uuid.UUID(bytes=bytes(h))),'name':'Radar','level':4,'bypassesPlayerLimit':False}]))" > "$S/ops.json"
(cd "$S" && PATH="$JAVA_HOME_21_X64/bin:$PATH" timeout 2400 $START > server.log 2>&1 &)
for i in $(seq 1 120); do sleep 4; grep -q 'Done (' "$S/server.log" && break; done
grep -q 'Done (' "$S/server.log" || { echo "::error::server did not start"; tail -40 "$S/server.log"; cp "$S/server.log" "$out/$mode-server.log"; exit 1; }
echo "server up: $(grep -m1 -o 'Done ([^)]*)' "$S/server.log")"

# ---- game options: Fancy graphics, 1920x1080, no first-launch screens, quiet
cat > "$G/options.txt" <<OPT
graphicsMode:1
renderDistance:10
simulationDistance:8
guiScale:3
ao:true
entityShadows:true
particles:0
maxFps:60
enableVsync:false
fullscreen:false
bobView:false
autoSuggestions:true
chatVisibility:0
onboardAccessibility:false
skipMultiplayerWarning:true
joinedFirstServer:true
pauseOnLostFocus:false
tutorialStep:none
narrator:0
soundCategory_master:0.0
OPT

(timeout 2400 xvfb-run -n 99 -s "-screen 0 1920x1080x24 +extension GLX +render -noreset -ac" \
   portablemc --main-dir "$G" start --jvm-policy mojang-then-system -u Radar "neoforge::$NF" \
   --jvm-arg=-Xmx4G,-Dstashradar.pageshots=$mode,-Dstashradar.pageshots.server=127.0.0.1:25565 --resolution 1920x1080 > game.log 2>&1 &)

for i in $(seq 1 480); do
  sleep 5
  grep -q "SRSHOT done" game.log && break
  grep -qE "Crash report saved|---- Minecraft Crash Report ----" game.log && break
  pgrep -f "portablemc" > /dev/null || break
done
sleep 5
pkill -f portablemc || true; pkill -f java || true; sleep 3
cp game.log "$out/$mode-game.log"; cp "$S/server.log" "$out/$mode-server.log" 2>/dev/null || true
cp "$G"/screenshots/*.png "$out/" 2>/dev/null || true
grep -E "SRSHOT|Stash Radar|\[CHAT\]" game.log | head -120
ls -la "$out"
grep -q "SRSHOT done" game.log && ! grep -q "SRSHOT done (with an error)" game.log
