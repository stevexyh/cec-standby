#!/usr/bin/env bash
# Offline only: no ADB, device discovery or network operations.
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/tests
javac -d build/tests src/io/github/stevexyh/cecstandby/{Profile,RecoveryPolicy,AppleDevices,LiveCecEvents,CecEvents,RawCecEvents}.java test/*.java
java -cp build/tests CecEventsTest
java -cp build/tests io.github.stevexyh.cecstandby.RawCecEventsTest
java -cp build/tests io.github.stevexyh.cecstandby.LiveCecEventsTest
java -cp build/tests io.github.stevexyh.cecstandby.ProfileTest
python3 -m unittest discover -s test -p 'test_*.py' -v
sh -n assets/health.sh
sh -n assets/start-bridge.sh
python3 - <<'CHECK'
import ast
from pathlib import Path
for path in [*Path('tools').glob('*.py'), Path('uninstall.py')]:
    ast.parse(path.read_text(), filename=str(path))
print('Python syntax checks passed')
CHECK
